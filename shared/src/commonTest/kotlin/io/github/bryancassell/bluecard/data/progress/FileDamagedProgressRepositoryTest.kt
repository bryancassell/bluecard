package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.systemFileSystem
import io.github.bryancassell.bluecard.testing.FakeClock
import io.github.bryancassell.bluecard.testing.FakeWarningLog
import io.github.bryancassell.bluecard.testing.TemporaryDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath

/**
 * Runs the repository contract against files in a temporary folder, and checks how damaged
 * progress is set aside. Each platform's test runs it with that platform's way of changing a
 * file's permissions and times, which common code has none of.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class FileDamagedProgressRepositoryTest : DamagedProgressRepositoryContract() {
    /**
     * Lets the owner write in [path], or stops them. Stopping them keeps files in a folder from
     * being made, moved or deleted, as long as the tests don't run as root, which CI doesn't.
     */
    protected abstract fun setWritable(path: Path, writable: Boolean)

    /** Sets when [path] was last changed, in milliseconds since 1970. */
    protected abstract fun setLastModified(path: Path, millis: Long)

    /** The message of Okio's error for [directory], which can't be made. */
    protected abstract fun cantCreateDirectoryMessage(directory: Path): String

    private val fileSystem = systemFileSystem
    private val temporary = TemporaryDirectory()

    // As Android's folders for the app's databases and for what backups leave out.
    private val noBackup = temporary.path / "no_backup"
    protected val database = temporary.path / "databases" / BlueCardDatabase.NAME

    private val folder = noBackup / FileDamagedProgressRepository.FOLDER_NAME
    private val damageFile = folder / "damage-found"
    private val clock = FakeClock(Instant.parse("2026-10-02T09:06:07.531Z"))
    protected val warningLog = FakeWarningLog()

    init {
        fileSystem.createDirectory(noBackup)
    }

    // Checks and changes files in place by default, so a test sees each when it's made. The
    // scope runs on the IO dispatcher, as the app's does.
    private fun newRepository(ioDispatcher: CoroutineDispatcher = Dispatchers.Unconfined) =
        FileDamagedProgressRepository(
            { noBackup },
            clock,
            warningLog,
            CoroutineScope(SupervisorJob() + ioDispatcher),
            ioDispatcher
        )

    override val repository = newRepository()

    override fun setAsideDamagedProgress() {
        writeDatabase("" to "damaged")
        repository.setAside(database)
    }

    @AfterTest
    fun deleteFiles() = temporary.delete()

    /** Writes the database file and those SQLite keeps beside it, each named by its suffix. */
    protected fun writeDatabase(vararg files: Pair<String, String>) {
        fileSystem.createDirectories(database.parent!!)
        for ((suffix, text) in files) write("$database$suffix".toPath(), text)
    }

    private fun write(file: Path, text: String) = fileSystem.write(file) { writeUtf8(text) }

    private fun readText(file: Path) = fileSystem.read(file) { readUtf8() }

    private fun exists(path: Path) = fileSystem.exists(path)

    /** The names of what's in [directory], in order. */
    private fun names(directory: Path) = fileSystem.list(directory).map { it.name }.sorted()

    /** The folders progress was set aside in. */
    private fun copies() = fileSystem.list(folder).filter { fileSystem.metadata(it).isDirectory }

    /** Runs [action] while the owner can't write in [path]. Inline, so [action] can suspend. */
    private inline fun <T> whileReadOnly(path: Path, action: () -> T): T {
        setWritable(path, false)
        try {
            return action()
        } finally {
            setWritable(path, true)
        }
    }

    @Test
    fun setAside_movesTheDatabaseAndItsFilesIntoAFolderNamedForWhen() {
        writeDatabase("" to "pages", "-wal" to "latest saves", "-shm" to "index")

        val setAside = repository.setAside(database)

        assertTrue(setAside)
        val copy = copies().single()
        assertTrue(copy.name.startsWith("2026-10-02T090607Z-"), copy.name)
        assertEquals("pages", readText(copy / "bluecard.db"))
        assertEquals("latest saves", readText(copy / "bluecard.db-wal"))
        assertEquals("index", readText(copy / "bluecard.db-shm"))
        assertFalse(exists(database))
        assertFalse(exists("$database-wal".toPath()))
        assertFalse(exists("$database-shm".toPath()))
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
        repository.setAside(database)
        writeDatabase("" to "a week of progress", "-journal" to "rollback")

        // In the same second, so the folders' names differ only by their suffix.
        repository.setAside(database)

        val byDatabase = copies().associateBy { readText(it / "bluecard.db") }
        assertEquals(setOf("years of progress", "a week of progress"), byDatabase.keys)
        assertEquals(
            listOf("bluecard.db", "bluecard.db-wal"),
            names(byDatabase.getValue("years of progress"))
        )
        assertEquals(
            listOf("bluecard.db", "bluecard.db-journal"),
            names(byDatabase.getValue("a week of progress"))
        )
    }

    @Test
    fun setAside_clearsRecordedDamage() {
        writeDatabase("" to "damaged")
        repository.recordDamage()

        repository.setAside(database)

        assertFalse(newRepository().isDamageRecorded(database))
    }

    @Test
    fun setAside_whenItWasAllMovedBefore_keepsWhatWasMoved_andClearsRecordedDamage() = runTest {
        writeDatabase("" to "damaged", "-wal" to "latest saves")
        repository.setAside(database)
        repository.dismissNotice()
        // As if the app had stopped after moving the files, before clearing the record.
        repository.recordDamage()

        repository.setAside(database)

        val copy = copies().single()
        assertEquals(listOf("bluecard.db", "bluecard.db-wal"), names(copy))
        assertEquals("damaged", readText(copy / "bluecard.db"))
        assertFalse(repository.isDamageRecorded(database))
        // There was nothing new to tell the scout.
        assertFalse(newRepository().observeNoticePending().first())
    }

    @Test
    fun setAside_again_whenTheNoticeCantBeWritten_stillSetsItAside() {
        setAsideDamagedProgress()
        writeDatabase("" to "damaged again")
        // The notice from the first, which the scout hasn't dismissed.
        whileReadOnly(folder / "notice-pending") { repository.setAside(database) }

        assertEquals(
            listOf("damaged", "damaged again"),
            copies().map { readText(it / "bluecard.db") }.sorted()
        )
        assertFalse(exists(database))
        assertEquals(
            List(2) { "Set aside a damaged database: $database" },
            warningLog.warnings.map { it.message }
        )
    }

    @Test
    fun setAside_whenRecordedDamageCantBeCleared_keepsIt_andWarns() {
        // As if the app had stopped after moving the files, before clearing the record.
        repository.recordDamage()
        // Stops the record being deleted.
        whileReadOnly(folder) { repository.setAside(database) }

        // The record stays, with the warning saying why. The database Room makes next is newer
        // than it, so isn't set aside for it.
        assertTrue(exists(damageFile))
        val warning = warningLog.warnings.single()
        assertEquals("DamagedProgress", warning.tag)
        assertEquals("Couldn't delete $damageFile", warning.message)
        assertTrue(warning.error is IOException, "${warning.error}")
    }

    /**
     * Dates [older] two minutes ago and [newer] one minute ago, as files written at two launches
     * would be, so their times can't tie.
     */
    private fun dateInOrder(older: Path, newer: Path) {
        val now = Clock.System.now().toEpochMilliseconds()
        setLastModified(older, now - 120_000)
        setLastModified(newer, now - 60_000)
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

        assertFalse(repository.isDamageRecorded(database))
        assertTrue(exists(damageFile))
    }

    @Test
    fun isDamageRecorded_whenTheRecordIsAsOldAsTheDatabase_isTrue() {
        writeDatabase("" to "damaged")
        repository.recordDamage()
        // In the same tick of the file clock.
        setLastModified(damageFile, fileSystem.metadata(database).lastModifiedAtMillis!!)

        assertTrue(repository.isDamageRecorded(database))
    }

    @Test
    fun clearLeftoverDamageRecord_deletesARecordOlderThanTheDatabase() {
        recordOlderThanTheDatabase()

        repository.clearLeftoverDamageRecord(database)

        assertFalse(exists(damageFile))
        assertEquals(emptyList<FakeWarningLog.Warning>(), warningLog.warnings)
    }

    @Test
    fun clearLeftoverDamageRecord_keepsARecordThatCounts() {
        writeDatabase("" to "damaged")
        repository.recordDamage()
        dateInOrder(older = database, newer = damageFile)

        repository.clearLeftoverDamageRecord(database)

        assertTrue(repository.isDamageRecorded(database))
    }

    @Test
    fun clearLeftoverDamageRecord_whenItCantBeDeleted_warns_andItStillDoesntCount() {
        recordOlderThanTheDatabase()
        // Stops the record being deleted.
        whileReadOnly(folder) { repository.clearLeftoverDamageRecord(database) }

        assertTrue(exists(damageFile))
        assertFalse(repository.isDamageRecorded(database))
        val warning = warningLog.warnings.single()
        assertEquals("DamagedProgress", warning.tag)
        assertEquals("Couldn't delete $damageFile", warning.message)
        assertTrue(warning.error is IOException, "${warning.error}")
    }

    @Test
    fun recordDamage_whenAnOlderRecordCantBeCleared_recordsItForTheDatabaseInUse() {
        recordOlderThanTheDatabase()
        whileReadOnly(folder) {
            // The launch's check, which can't delete it.
            repository.clearLeftoverDamageRecord(database)
            assertTrue(exists(damageFile))

            repository.recordDamage()
        }

        assertTrue(repository.isDamageRecorded(database))
        // Only the check warned, so the record was written.
        assertEquals(listOf("Couldn't delete $damageFile"), warningLog.warnings.map { it.message })
    }

    @Test
    fun recordDamage_isKeptAfterTheAppReopens_withoutTellingTheScoutYet() = runTest {
        writeDatabase("" to "damaged")

        repository.recordDamage()

        val reopened = newRepository()
        assertTrue(reopened.isDamageRecorded(database))
        assertFalse(reopened.observeNoticePending().first())
        // Only set aside when the app next opens the database.
        assertTrue(exists(database))
    }

    @Test
    fun recordDamage_whenTheFolderCantBeMade_recordsNothing() {
        whileReadOnly(noBackup) { repository.recordDamage() }

        assertFalse(repository.isDamageRecorded(database))
        val warning = warningLog.warnings.single()
        assertEquals("DamagedProgress", warning.tag)
        assertEquals("Couldn't record a damaged database", warning.message)
        // The folder that couldn't be made, rather than some other failure.
        assertEquals(cantCreateDirectoryMessage(folder), warning.error?.message)
    }

    @Test
    fun setAside_whenTheFolderCantBeMade_deletesTheDatabase_andTellsTheScout() = runTest {
        writeDatabase(
            "" to "damaged",
            "-wal" to "latest saves",
            "-shm" to "index",
            "-journal" to "rollback"
        )
        // Stops the folder being made.
        val setAside = whileReadOnly(noBackup) { repository.setAside(database) }

        assertTrue(setAside)
        assertFalse(exists(folder))
        // Deleted, so SQLite creates a new database rather than opening the damaged one again.
        assertEquals(emptyList(), names(database.parent!!))
        assertTrue(repository.observeNoticePending().first())
        // The only record that the scout's progress was deleted rather than kept.
        val warning = warningLog.warnings.single()
        assertEquals("DamagedProgress", warning.tag)
        assertEquals(
            "Couldn't set aside a damaged database, so deleted it: $database",
            warning.message
        )
        // The folder that couldn't be made, rather than some other failure.
        assertEquals(cantCreateDirectoryMessage(folder), warning.error?.message)
    }

    @Test
    fun setAside_whenAFileBesideTheDatabaseCantBeDeleted_keepsTheDatabase_andDoesntTellTheScout() =
        runTest {
            writeDatabase("" to "damaged", "-shm" to "index")
            // A log that can't be deleted, as a folder with something in it can't.
            val log = "$database-wal".toPath()
            fileSystem.createDirectory(log)
            write(log / "page", "saves")
            // Stops the folder being made.
            val setAside = whileReadOnly(noBackup) { repository.setAside(database) }

            assertFalse(setAside)
            // Stopped at the log, so the database never opens again without it.
            assertEquals("damaged", readText(database))
            assertEquals("index", readText("$database-shm".toPath()))
            assertFalse(repository.observeNoticePending().first())
            val warning = warningLog.warnings.single()
            assertEquals(
                "Couldn't set aside or delete a damaged database: $database",
                warning.message
            )
            assertEquals(cantCreateDirectoryMessage(folder), warning.error?.message)
            val suppressed = warning.error?.suppressedExceptions
            assertTrue(suppressed?.single() is IOException, "$suppressed")
        }

    @Test
    fun setAside_whenTheDatabaseCantBeMovedOrDeleted_keepsRecordedDamage_withoutANotice() =
        runTest {
            writeDatabase("" to "damaged")
            // Stops the database being moved or deleted.
            val setAside = whileReadOnly(database.parent!!) { repository.setAside(database) }

            assertFalse(setAside)
            assertEquals("damaged", readText(database))
            // The folder made for it, with nothing moved in, isn't left looking like a copy.
            assertEquals(emptyList(), copies())
            // So the next launch tries again.
            assertTrue(newRepository().isDamageRecorded(database))
            assertFalse(repository.observeNoticePending().first())
            assertFalse(newRepository().observeNoticePending().first())
            assertEquals(
                listOf("Couldn't set aside or delete a damaged database: $database"),
                warningLog.warnings.map { it.message }
            )
        }

    @Test
    fun setAside_again_whenItCantBeMovedOrDeleted_stillOwesTheScoutTheFirstNotice() = runTest {
        setAsideDamagedProgress()
        writeDatabase("" to "damaged again")
        whileReadOnly(database.parent!!) { repository.setAside(database) }

        assertEquals("damaged again", readText(database))
        assertTrue(newRepository().observeNoticePending().first())
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
        assertEquals("damaged", readText(copies().single() / "bluecard.db"))
    }

    @Test
    fun dismissNotice_whenTheNoticeCantBeDeleted_dismissesItUntilTheAppReopens() = runTest {
        setAsideDamagedProgress()
        // Stops the file being deleted.
        whileReadOnly(folder) { repository.dismissNotice() }

        assertFalse(repository.observeNoticePending().first())
        assertTrue(newRepository().observeNoticePending().first())
        // After the warning that the database was set aside.
        val warning = warningLog.warnings.drop(1).single()
        assertEquals("DamagedProgress", warning.tag)
        assertEquals("Couldn't delete ${folder / "notice-pending"}", warning.message)
        assertTrue(warning.error is IOException, "${warning.error}")
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

    @Test
    fun dismissNotice_deletesTheNoticeOnTheIoDispatcher() = runTest {
        // A scheduler of its own, so the test runs the IO dispatcher's work apart from the rest.
        val io = TestCoroutineScheduler()
        val repository = newRepository(StandardTestDispatcher(io))
        writeDatabase("" to "damaged")
        repository.setAside(database)
        val notice = folder / "notice-pending"

        launch { repository.dismissNotice() }
        advanceUntilIdle()
        assertTrue(exists(notice))

        io.advanceUntilIdle()
        assertFalse(exists(notice))
    }
}
