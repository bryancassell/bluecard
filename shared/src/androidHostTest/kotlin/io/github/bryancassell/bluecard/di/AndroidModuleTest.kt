package io.github.bryancassell.bluecard.di

import android.content.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.ContentResolverDocuments
import io.github.bryancassell.bluecard.data.Documents
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.data.progress.BadgeProgress
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.BlueCardDatabase
import io.github.bryancassell.bluecard.data.progress.DamagedProgressRepository
import io.github.bryancassell.bluecard.data.progress.FileDamagedProgressRepository
import io.github.bryancassell.bluecard.data.report.PdfDocumentWriter
import io.github.bryancassell.bluecard.data.report.PdfReportRepository
import io.github.bryancassell.bluecard.data.report.ReportPdfWriter
import io.github.bryancassell.bluecard.data.report.ReportRepository
import io.github.bryancassell.bluecard.testing.AppKoinRule
import io.github.bryancassell.bluecard.testing.testIoDispatcherModule
import io.github.bryancassell.bluecard.text.AndroidPhoneNumberFormat
import io.github.bryancassell.bluecard.text.AndroidTextRules
import io.github.bryancassell.bluecard.text.PhoneNumberFormat
import io.github.bryancassell.bluecard.text.TextRules
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules

/** Checks what [androidModule] provides, from Koin started with the app's modules. */
@RunWith(AndroidJUnit4::class)
class AndroidModuleTest {
    @get:Rule
    val appKoin = AppKoinRule()

    private val koin get() = appKoin.koin

    private val context = ApplicationProvider.getApplicationContext<Context>()

    // Built only by the tests that use it.
    private val database = lazy { koin.get<BlueCardDatabase>() }

    @Before
    fun useTestDispatcher() {
        loadKoinModules(testIoDispatcherModule)
    }

    @After
    fun closeDatabase() {
        if (database.isInitialized()) database.value.close()
    }

    @Test
    fun database_isStoredInDatabaseFile() = runTest {
        database.value.progressDao().insertBadge(
            BadgeProgress("camping", LocalDate(2026, 1, 1), LocalDate(2026, 3, 1))
        )
        database.value.close()

        // Installed apps keep their progress here: moving or renaming the file would lose it
        // on update, and the backup rules cover this location (see BackupRulesTest).
        val stored = BundledSQLiteDriver().open(context.getDatabasePath("bluecard.db").path).use {
            it.prepare("SELECT badgeId FROM badge_progress").use { badges ->
                badges.step()
                badges.getText(0)
            }
        }
        assertEquals("camping", stored)
    }

    @Test
    fun damagedDatabase_isSetAside_andTheScoutIsTold() = runTest {
        // Room opens the file on first use, so it's damaged before then.
        context.getDatabasePath("bluecard.db").apply { parentFile!!.mkdirs() }
            .writeBytes(ByteArray(8192) { 7 })

        val progress = database.value.progressDao().observeAll().first()

        assertEquals(emptyList<BadgeProgressDetails>(), progress)
        assertTrue(koin.get<DamagedProgressRepository>().observeNoticePending().first())
    }

    @Test
    fun damagedProgressRepository_isTheOneTheDatabaseSetsProgressAsideWith() {
        // Otherwise the notice wouldn't show until the app reopened.
        assertSame(
            koin.get<FileDamagedProgressRepository>(),
            koin.get<DamagedProgressRepository>()
        )
    }

    @Test
    fun profile_isStoredInProfileFile() {
        runBlocking { koin.get<ProfileRepository>().saveProfile(Profile("Alex Scout", "123")) }

        // Installed apps keep their profile here: renaming the file would lose it on update.
        assertTrue(File(context.filesDir, "datastore/profile.preferences_pb").exists())
    }

    @Test
    fun warningLog_isLogcat() {
        assertSame(LogcatWarningLog, koin.get<WarningLog>())
    }

    @Test
    fun reportRepository_isPdfReportRepository() {
        assertTrue(koin.get<ReportRepository>() is PdfReportRepository)
    }

    @Test
    fun pdfWriter_isPdfDocumentWriter() {
        assertTrue(koin.get<ReportPdfWriter>() is PdfDocumentWriter)
    }

    @Test
    fun documents_isContentResolverDocuments() {
        assertTrue(koin.get<Documents>() is ContentResolverDocuments)
    }

    @Test
    fun textRules_isAndroidTextRules() {
        assertTrue(koin.get<TextRules>() is AndroidTextRules)
    }

    @Test
    fun phoneNumberFormat_isAndroidPhoneNumberFormat() {
        assertTrue(koin.get<PhoneNumberFormat>() is AndroidPhoneNumberFormat)
    }
}
