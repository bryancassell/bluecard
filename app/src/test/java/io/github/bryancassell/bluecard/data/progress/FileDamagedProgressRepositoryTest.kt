package io.github.bryancassell.bluecard.data.progress

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.testing.FakeClock
import java.io.File
import java.time.Instant
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
    private val clock = FakeClock(Instant.parse("2026-10-02T09:06:07.531Z"))

    // Checks and changes files in place by default, so a test sees each when it's made.
    private fun newRepository(ioDispatcher: CoroutineDispatcher = Dispatchers.Unconfined) =
        FileDamagedProgressRepository(
            context,
            clock,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            ioDispatcher
        )

    override val repository = newRepository()

    override fun setAsideDamagedProgress() {
        writeDatabase("" to "damaged")
        repository.setAside(database)
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

        repository.setAside(database)

        val copy = copies().single()
        assertTrue(copy.name, copy.name.startsWith("2026-10-02T090607Z-"))
        assertEquals("pages", File(copy, "bluecard.db").readText())
        assertEquals("latest saves", File(copy, "bluecard.db-wal").readText())
        assertEquals("index", File(copy, "bluecard.db-shm").readText())
        assertFalse(database.exists())
        assertFalse(File(database.path + "-wal").exists())
        assertFalse(File(database.path + "-shm").exists())
    }

    @Test
    fun setAside_again_keepsTheCopyFromBefore() {
        writeDatabase("" to "years of progress", "-wal" to "latest saves")
        repository.setAside(database)
        writeDatabase("" to "a week of progress", "-journal" to "rollback")

        // In the same second, so the folders' names differ only by their suffix.
        repository.setAside(database)

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
    fun setAside_whenAnotherConnectionAlreadyMovedIt_keepsWhatWasMoved() {
        writeDatabase("" to "damaged", "-wal" to "latest saves")
        repository.setAside(database)

        repository.setAside(database)

        val copy = copies().single()
        assertEquals(listOf("bluecard.db", "bluecard.db-wal"), copy.list()!!.sorted())
        assertEquals("damaged", File(copy, "bluecard.db").readText())
    }

    @Test
    fun setAside_whenTheFolderCantBeMade_deletesTheDatabase_andTellsTheScout() = runTest {
        writeDatabase("" to "damaged", "-wal" to "latest saves")
        // Stops the folder being made, as long as the tests don't run as root, which CI doesn't.
        val noBackup = context.noBackupFilesDir
        noBackup.setWritable(false)
        try {
            repository.setAside(database)
        } finally {
            noBackup.setWritable(true)
        }

        assertFalse(folder.exists())
        // Deleted, so SQLite creates a new database rather than opening the damaged one again.
        assertFalse(database.exists())
        assertFalse(File(database.path + "-wal").exists())
        assertTrue(repository.observeNoticePending().first())
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
    fun dismissNotice_finishesEvenIfItsCallerIsCancelled() = runTest {
        // Holds the dismissal's file work until the test runs it, after the caller is gone.
        val repository = newRepository(StandardTestDispatcher(testScheduler))
        writeDatabase("" to "damaged")
        repository.setAside(database)

        val caller = launch(start = CoroutineStart.UNDISPATCHED) { repository.dismissNotice() }
        caller.cancel()
        advanceUntilIdle()

        assertFalse(newRepository().observeNoticePending().first())
    }
}
