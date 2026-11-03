package io.github.bryancassell.bluecard.di

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.bryancassell.bluecard.data.Documents
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.data.progress.BadgeProgress
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.BlueCardDatabase
import io.github.bryancassell.bluecard.data.progress.DamagedProgressRepository
import io.github.bryancassell.bluecard.data.progress.FileDamagedProgressRepository
import io.github.bryancassell.bluecard.data.report.ReportRepository
import io.github.bryancassell.bluecard.data.systemFileSystem
import io.github.bryancassell.bluecard.initKoin
import io.github.bryancassell.bluecard.text.IosTextRules
import io.github.bryancassell.bluecard.text.PhoneNumberFormat
import io.github.bryancassell.bluecard.text.TextRules
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.koin.core.Koin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.mp.KoinPlatform

/**
 * Checks what [iosModule] provides, from Koin started as the iOS app starts it. The files go in
 * the simulator's Application Support directory, which every test run shares, so each test starts
 * without them, as a newly installed app does, and deletes them after.
 */
class IosModuleTest {
    private val fileSystem = systemFileSystem
    private val directory = applicationSupportDirectory()
    private val databaseFile = directory / BlueCardDatabase.NAME

    private lateinit var koin: Koin

    // Built only by the tests that use it.
    private val database = lazy { koin.get<BlueCardDatabase>() }

    @BeforeTest
    fun startApp() {
        deleteAppFiles()
        initKoin()
        koin = KoinPlatform.getKoin()
    }

    @AfterTest
    fun stopApp() {
        if (database.isInitialized()) database.value.close()
        // Ends the profile's DataStore, which allows one instance per file in a process.
        koin.get<CoroutineScope>(named<ApplicationScope>()).cancel()
        stopKoin()
        deleteAppFiles()
    }

    private fun deleteAppFiles() {
        for (suffix in listOf("", "-wal", "-shm", "-journal")) {
            fileSystem.delete(directory / "${BlueCardDatabase.NAME}$suffix")
        }
        fileSystem.delete(directory / PROFILE_FILE)
        fileSystem.deleteRecursively(directory / FileDamagedProgressRepository.FOLDER_NAME)
    }

    @Test
    fun database_isStoredInApplicationSupport() = runTest {
        database.value.progressDao().insertBadge(
            BadgeProgress("camping", LocalDate(2026, 1, 1), LocalDate(2026, 3, 1))
        )
        database.value.close()

        // Installed apps keep their progress here: moving or renaming the file would lose it on
        // update.
        val stored = BundledSQLiteDriver().open(databaseFile.toString()).use {
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
        fileSystem.write(databaseFile) { write(ByteArray(8192) { 7 }) }

        val progress = database.value.progressDao().observeAll().first()

        assertEquals(emptyList<BadgeProgressDetails>(), progress)
        assertTrue(koin.get<DamagedProgressRepository>().observeNoticePending().first())
        val copy = fileSystem.list(directory / FileDamagedProgressRepository.FOLDER_NAME)
            .single { fileSystem.metadata(it).isDirectory }
        assertEquals(8192L, fileSystem.metadata(copy / BlueCardDatabase.NAME).size)
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
    fun profile_isStoredInApplicationSupport() = runTest {
        koin.get<ProfileRepository>().saveProfile(Profile("Alex Scout", "123"))

        // Installed apps keep their profile here: renaming the file would lose it on update.
        assertTrue(fileSystem.exists(directory / PROFILE_FILE))
    }

    @Test
    fun applicationSupportDirectory_isADirectory() {
        assertTrue(fileSystem.metadata(directory).isDirectory)
    }

    @Test
    fun warningLog_isNSLog() {
        assertSame(NSLogWarningLog, koin.get<WarningLog>())
    }

    // Passing the text to NSLog as an argument crashed the test process, as setting damaged
    // progress aside first warned. Nothing reads the log back, so this only checks it doesn't.
    @Test
    fun warningLog_writesAnyText() {
        NSLogWarningLog.warn("Tag", "%@ %s 100% Alex علی", IllegalStateException("%@ failed"))
    }

    @Test
    fun reportRepository_isUnfinished() {
        assertSame(UnfinishedReports, koin.get<ReportRepository>())
    }

    @Test
    fun documents_isUnfinished() {
        assertSame(UnfinishedDocuments, koin.get<Documents>())
    }

    @Test
    fun textRules_isIosTextRules() {
        assertIs<IosTextRules>(koin.get<TextRules>())
    }

    @Test
    fun phoneNumberFormat_leavesNumbersAsTyped() = runTest {
        assertEquals("5551234567", koin.get<PhoneNumberFormat>().format("5551234567"))
    }

    private companion object {
        const val PROFILE_FILE = "profile.preferences_pb"
    }
}
