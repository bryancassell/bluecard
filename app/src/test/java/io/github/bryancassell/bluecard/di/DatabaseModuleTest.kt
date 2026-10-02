package io.github.bryancassell.bluecard.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.BlueCardDatabase
import io.github.bryancassell.bluecard.data.progress.DamagedProgressRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Checks the production database from DatabaseModule. */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class DatabaseModuleTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: BlueCardDatabase

    @Inject
    lateinit var damagedProgressRepository: DamagedProgressRepository

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun closeDatabase() {
        // Skipped if injection failed, so the report shows only that failure.
        if (::database.isInitialized) database.close()
    }

    @Test
    fun database_isStoredInDatabaseFile() {
        val path = database.openHelper.writableDatabase.path!!

        // Installed apps keep their progress here: moving or renaming the file would lose it
        // on update, and the backup rules cover this location (see BackupRulesTest).
        assertEquals(context.getDatabasePath("bluecard.db").canonicalFile, File(path).canonicalFile)
    }

    @Test
    fun damagedDatabase_isSetAside_andTheScoutIsTold() = runTest {
        // Room opens the file on first use, so it's damaged before then.
        context.getDatabasePath("bluecard.db").apply { parentFile!!.mkdirs() }
            .writeBytes(ByteArray(8192) { 7 })

        val progress = database.progressDao().observeAll().first()

        assertEquals(emptyList<BadgeProgressDetails>(), progress)
        assertTrue(damagedProgressRepository.observeNoticePending().first())
    }
}
