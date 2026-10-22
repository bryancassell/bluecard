package io.github.bryancassell.bluecard.data.progress

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.testing.FakeClock
import io.github.bryancassell.bluecard.testing.FakeWarningLog
import java.io.File
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.IOException
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the repository contract against files in Robolectric's app folders, and checks how
 * damaged progress is set aside.
 */
@RunWith(AndroidJUnit4::class)
class FileDamagedProgressRepositoryTest : DamagedProgressRepositoryContract() {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = context.getDatabasePath(BlueCardDatabase.NAME)
    private val folder = File(context.noBackupFilesDir, FileDamagedProgressRepository.FOLDER_NAME)
    private val damageFile = File(folder, "damage-found")
    private val clock = FakeClock(Instant.parse("2026-10-02T09:06:07.531Z"))
    private val warningLog = FakeWarningLog()

    // Checks and changes files in place by default, so a test sees each when it's made. The
    // scope runs on the IO dispatcher, as the app's does.
    private fun newRepository(ioDispatcher: CoroutineDispatcher = Dispatchers.Unconfined) =
        FileDamagedProgressRepository(
            context,
            clock,
            warningLog,
            CoroutineScope(SupervisorJob() + ioDispatcher),
            ioDispatcher
        )

    override val repository = newRepository()

    override fun setAsideDamagedProgress() {
        writeDatabase("" to "damaged")
        repository.setAside(database.toOkioPath())
    }

    /** Writes the database file and those SQLite keeps beside it, each named by its suffix. */
    private fun writeDatabase(vararg files: Pair<String, String>) {
        database.parentFile!!.mkdirs()
        for ((suffix, text) in files) File(database.path + suffix).writeText(text)
    }

    /** The folders progress was set aside in. */
    private fun copies() = folder.listFiles { file -> file.isDirectory }!!.toList()

    @Test
    fun setAside_movesTheDatabaseAndItsFilesIntoAFolderNamedForWhen() {
        writeDatabase("" to "pages", "-wal" to "latest saves", "-shm" to "index")

        repository.setAside(database.toOkioPath())

        val copy = copies().single()
        assertTrue(copy.name, copy.name.startsWith("2026-10-02T090607Z-"))
        assertEquals("pages", File(copy, "bluecard.db").readText())
        assertEquals("latest saves", File(copy, "bluecard.db-wal").readText())
        assertEquals("index", File(copy, "bluecard.db-shm").readText())
        assertFalse(database.exists())
        assertFalse(File(database.path + "-wal").exists())
        assertFalse(File(database.path + "-shm").exists())
        assertEquals(
            listOf(
                FakeWarningLog.Warning(
                    "DamagedProgress",
                    "Set aside a damaged database: $database",
                    null
                )
            ),
            warningLog.warnings
        )
    }

    @Test
    fun setAside_again_keepsTheCopyFromBefore() {
        writeDatabase("" to "years of progress", "-wal" to "latest saves")
        repository.setAside(database.toOkioPath())
        writeDatabase("" to "a week of progress", "-journal" to "rollback")

        // In the same second, so the folders' names differ only by their suffix.
        repository.setAside(database.toOkioPath())

        val byDatabase = copies().associateBy { File(it, "bluecard.db").readText() }
        assertEquals(setOf("years of progress", "a week of progress"), byDatabase.keys)
        assertEquals(
            listOf("bluecard.db", "bluecard.db-wal"),
            byDatabase.getValue("years of progress").list()!!.sorted()
        )
        assertEquals(
            listOf("bluecard.db", "bluecard.db-journal"),
            byDatabase.getValue("a week of progress").list()!!.sorted()
        )
    }

    @Test
    fun setAside_clearsRecordedDamage() {
        writeDatabase("" to "damaged")
        repository.recordDamage()

        repository.setAside(database.toOkioPath())

        assertFalse(newRepository().isDamageRecorded(database.toOkioPath()))
    }

    @Test
    fun setAside_whenItWasAllMovedBefore_keepsWhatWasMoved_andClearsRecordedDamage() = runTest {
        writeDatabase("" to "damaged", "-wal" to "latest saves")
        repository.setAside(database.toOkioPath())
        repository.dismissNotice()
        // As if the app had stopped after moving the files, before clearing the record.
        repository.recordDamage()

        repository.setAside(database.toOkioPath())

        val copy = copies().single()
        assertEquals(listOf("bluecard.db", "bluecard.db-wal"), copy.list()!!.sorted())
        assertEquals("damaged", File(copy, "bluecard.db").readText())
        assertFalse(repository.isDamageRecorded(database.toOkioPath()))
        // There was nothing new to tell the scout.
        assertFalse(newRepository().observeNoticePending().first())
    }

    @Test
    fun setAside_again_whenTheNoticeCantBeWritten_stillSetsItAside() {
        setAsideDamagedProgress()
        // The notice from the first, which the scout hasn't dismissed.
        val notice = File(folder, "notice-pending")
        notice.setWritable(false)
        writeDatabase("" to "damaged again")
        try {
            repository.setAside(database.toOkioPath())
        } finally {
            notice.setWritable(true)
        }

        assertEquals(
            listOf("damaged", "damaged again"),
            copies().map { File(it, "bluecard.db").readText() }.sorted()
        )
        assertFalse(database.exists())
        assertEquals(
            List(2) { "Set aside a damaged database: $database" },
            warningLog.warnings.map { it.message }
        )
    }

    @Test
    fun setAside_whenRecordedDamageCantBeCleared_keepsIt_andWarns() {
        // As if the app had stopped after moving the files, before clearing the record.
        repository.recordDamage()
        // Stops the record being deleted, as long as the tests don't run as root.
        folder.setWritable(false)
        try {
            repository.setAside(database.toOkioPath())
        } finally {
            folder.setWritable(true)
        }

        // The record stays, with the warning saying why. The database Room makes next is newer
        // than it, so isn't set aside for it.
        assertTrue(damageFile.exists())
        val warning = warningLog.warnings.single()
        assertEquals("DamagedProgress", warning.tag)
        assertEquals("Couldn't delete $damageFile", warning.message)
        assertTrue("${warning.error}", warning.error is IOException)
    }

    /** As when a set-aside couldn't delete the record and Room made a new database later. */
    private fun recordOlderThanTheDatabase() {
        repository.recordDamage()
        writeDatabase("" to "new progress")
        dateInOrder(older = damageFile, newer = database)
    }

    @Test
    fun isDamageRecorded_whenTheRecordIsOlderThanTheDatabase_isFalse_andKeepsIt() {
        recordOlderThanTheDatabase()

        assertFalse(repository.isDamageRecorded(database.toOkioPath()))
        assertTrue(damageFile.exists())
    }

    @Test
    fun isDamageRecorded_whenTheRecordIsAsOldAsTheDatabase_isTrue() {
        writeDatabase("" to "damaged")
        repository.recordDamage()
        // In the same tick of the file clock.
        assertTrue(damageFile.setLastModified(database.lastModified()))

        assertTrue(repository.isDamageRecorded(database.toOkioPath()))
    }

    @Test
    fun clearLeftoverDamageRecord_deletesARecordOlderThanTheDatabase() {
        recordOlderThanTheDatabase()

        repository.clearLeftoverDamageRecord(database.toOkioPath())

        assertFalse(damageFile.exists())
        assertEquals(emptyList<FakeWarningLog.Warning>(), warningLog.warnings)
    }

    @Test
    fun clearLeftoverDamageRecord_keepsARecordThatCounts() {
        writeDatabase("" to "damaged")
        repository.recordDamage()
        dateInOrder(older = database, newer = damageFile)

        repository.clearLeftoverDamageRecord(database.toOkioPath())

        assertTrue(repository.isDamageRecorded(database.toOkioPath()))
    }

    @Test
    fun clearLeftoverDamageRecord_withNoRecord_deletesNothing() {
        writeDatabase("" to "progress")
        // Okio's delete would take the interrupt for itself and fail (Okio 3.18.2).
        Thread.currentThread().interrupt()
        val stillInterrupted = try {
            repository.clearLeftoverDamageRecord(database.toOkioPath())
            Thread.interrupted()
        } finally {
            // So the interrupt can't reach the next test.
            Thread.interrupted()
        }

        assertTrue(stillInterrupted)
        assertEquals(emptyList<FakeWarningLog.Warning>(), warningLog.warnings)
    }

    @Test
    fun clearLeftoverDamageRecord_whenItCantBeDeleted_warns_andItStillDoesntCount() {
        recordOlderThanTheDatabase()
        // Stops the record being deleted, as long as the tests don't run as root.
        folder.setWritable(false)
        try {
            repository.clearLeftoverDamageRecord(database.toOkioPath())
        } finally {
            folder.setWritable(true)
        }

        assertTrue(damageFile.exists())
        assertFalse(repository.isDamageRecorded(database.toOkioPath()))
        val warning = warningLog.warnings.single()
        assertEquals("DamagedProgress", warning.tag)
        assertEquals("Couldn't delete $damageFile", warning.message)
        assertTrue("${warning.error}", warning.error is IOException)
    }

    @Test
    fun recordDamage_whenAnOlderRecordCantBeCleared_recordsItForTheDatabaseInUse() {
        recordOlderThanTheDatabase()
        folder.setWritable(false)
        try {
            // The launch's check, which can't delete it.
            repository.clearLeftoverDamageRecord(database.toOkioPath())
            assertTrue(damageFile.exists())

            repository.recordDamage()
        } finally {
            folder.setWritable(true)
        }

        assertTrue(repository.isDamageRecorded(database.toOkioPath()))
        // Only the check warned, so the record was written.
        assertEquals(listOf("Couldn't delete $damageFile"), warningLog.warnings.map { it.message })
    }

    @Test
    fun recordDamage_isKeptAfterTheAppReopens_withoutTellingTheScoutYet() = runTest {
        writeDatabase("" to "damaged")

        repository.recordDamage()

        val reopened = newRepository()
        assertTrue(reopened.isDamageRecorded(database.toOkioPath()))
        assertFalse(reopened.observeNoticePending().first())
        // Only set aside when the app next opens the database.
        assertTrue(database.exists())
    }

    @Test
    fun recordDamage_whenTheFolderCantBeMade_recordsNothing() {
        val noBackup = context.noBackupFilesDir
        noBackup.setWritable(false)
        try {
            repository.recordDamage()
        } finally {
            noBackup.setWritable(true)
        }

        assertFalse(repository.isDamageRecorded(database.toOkioPath()))
        val warning = warningLog.warnings.single()
        assertEquals("DamagedProgress", warning.tag)
        assertEquals("Couldn't record a damaged database", warning.message)
        // The folder that couldn't be made, rather than some other failure.
        assertEquals("failed to create directory: $folder", warning.error?.message)
    }

    @Test
    fun setAside_whenTheFolderCantBeMade_deletesTheDatabase_andTellsTheScout() = runTest {
        writeDatabase("" to "damaged", "-wal" to "latest saves")
        // Stops the folder being made, as long as the tests don't run as root, which CI doesn't.
        val noBackup = context.noBackupFilesDir
        noBackup.setWritable(false)
        try {
            repository.setAside(database.toOkioPath())
        } finally {
            noBackup.setWritable(true)
        }

        assertFalse(folder.exists())
        // Deleted, so SQLite creates a new database rather than opening the damaged one again.
        assertFalse(database.exists())
        assertFalse(File(database.path + "-wal").exists())
        assertTrue(repository.observeNoticePending().first())
        // The only record that the scout's progress was deleted rather than kept.
        val warning = warningLog.warnings.single()
        assertEquals("DamagedProgress", warning.tag)
        assertEquals(
            "Couldn't set aside a damaged database, so deleted it: $database",
            warning.message
        )
        // The folder that couldn't be made, rather than some other failure.
        assertEquals("failed to create directory: $folder", warning.error?.message)
    }

    @Test
    fun observeNoticePending_afterTheAppReopens_isStillTrue() = runTest {
        setAsideDamagedProgress()

        assertTrue(newRepository().observeNoticePending().first())
    }

    @Test
    fun dismissNotice_keepsItDismissedAfterTheAppReopens_andKeepsTheDamagedProgress() = runTest {
        setAsideDamagedProgress()

        repository.dismissNotice()

        assertFalse(newRepository().observeNoticePending().first())
        assertEquals("damaged", File(copies().single(), "bluecard.db").readText())
    }

    @Test
    fun dismissNotice_whenTheNoticeCantBeDeleted_dismissesItUntilTheAppReopens() = runTest {
        setAsideDamagedProgress()
        // Stops the file being deleted, as long as the tests don't run as root.
        folder.setWritable(false)
        try {
            repository.dismissNotice()
        } finally {
            folder.setWritable(true)
        }

        assertFalse(repository.observeNoticePending().first())
        assertTrue(newRepository().observeNoticePending().first())
        // After the warning that the database was set aside.
        val warning = warningLog.warnings.drop(1).single()
        assertEquals("DamagedProgress", warning.tag)
        assertEquals("Couldn't delete ${File(folder, "notice-pending")}", warning.message)
        assertTrue("${warning.error}", warning.error is IOException)
    }

    @Test
    fun dismissNotice_finishesEvenIfItsCallerIsCancelled() = runTest {
        // Holds the dismissal's file work until the test runs it, after the caller is gone.
        val repository = newRepository(StandardTestDispatcher(testScheduler))
        writeDatabase("" to "damaged")
        repository.setAside(database.toOkioPath())

        val caller = launch(start = CoroutineStart.UNDISPATCHED) { repository.dismissNotice() }
        caller.cancel()
        advanceUntilIdle()

        assertFalse(newRepository().observeNoticePending().first())
    }

    @Test
    fun dismissNotice_deletesTheNoticeOnTheIoDispatcher() = runTest {
        // Holds the file work until the test runs it, so a delete in the caller's thread shows.
        val repository = newRepository(StandardTestDispatcher(testScheduler))
        writeDatabase("" to "damaged")
        repository.setAside(database.toOkioPath())
        val notice = File(folder, "notice-pending")

        launch(start = CoroutineStart.UNDISPATCHED) { repository.dismissNotice() }

        assertTrue(notice.exists())
        advanceUntilIdle()
        assertFalse(notice.exists())
    }
}
