package io.github.bryancassell.bluecard.data.progress

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

    // Checks files in place, so a test sees each check when it's made.
    private fun newRepository() = FileDamagedProgressRepository(context, Dispatchers.Unconfined)

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

    private fun setAsideFiles() = folder.list()!!.filter { it.startsWith(BlueCardDatabase.NAME) }

    @Test
    fun setAside_movesTheDatabaseAndItsFilesIntoTheNoBackupFolder() {
        writeDatabase("" to "pages", "-wal" to "latest saves", "-shm" to "index")

        repository.setAside(database)

        assertEquals("pages", File(folder, "bluecard.db").readText())
        assertEquals("latest saves", File(folder, "bluecard.db-wal").readText())
        assertEquals("index", File(folder, "bluecard.db-shm").readText())
        assertFalse(database.exists())
        assertFalse(File(database.path + "-wal").exists())
        assertFalse(File(database.path + "-shm").exists())
    }

    @Test
    fun setAside_replacesProgressSetAsideBefore() {
        writeDatabase("" to "first", "-wal" to "first saves")
        repository.setAside(database)
        writeDatabase("" to "second", "-journal" to "second rollback")

        repository.setAside(database)

        assertEquals(listOf("bluecard.db", "bluecard.db-journal"), setAsideFiles().sorted())
        assertEquals("second", File(folder, "bluecard.db").readText())
    }

    @Test
    fun setAside_whenAnotherConnectionAlreadyMovedIt_keepsWhatWasMoved() {
        writeDatabase("" to "damaged", "-wal" to "latest saves")
        repository.setAside(database)

        repository.setAside(database)

        assertEquals(listOf("bluecard.db", "bluecard.db-wal"), setAsideFiles().sorted())
        assertEquals("damaged", File(folder, "bluecard.db").readText())
    }

    @Test
    fun setAside_whenTheFolderCantBeMade_deletesTheDatabase_andTellsTheScout() = runTest {
        writeDatabase("" to "damaged", "-wal" to "latest saves")
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
        assertEquals("damaged", File(folder, "bluecard.db").readText())
    }
}
