package io.github.bryancassell.bluecard.data.progress

import androidx.room3.RoomDatabase
import androidx.room3.executeSQL
import androidx.room3.useWriterConnection
import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteException
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.bryancassell.bluecard.data.Lock
import io.github.bryancassell.bluecard.data.systemFileSystem
import io.github.bryancassell.bluecard.testing.TemporaryDirectory
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath

/**
 * Runs the repository contract against Room on the SQLite the app bundles (BundledSQLiteDriver),
 * and checks how Room's errors reach the scout and that writes outlive their callers. Each
 * platform's test runs it with that platform's in-memory database builder, since Room's errors,
 * its lock file and the threads it writes on are the platform's own.
 */
abstract class RoomProgressRepositoryTest : ProgressRepositoryContract() {
    /** A builder for a database that's only in memory. */
    protected abstract fun inMemoryDatabaseBuilder(): RoomDatabase.Builder<BlueCardDatabase>

    /** The name of the thread that calls it, with an ID no other running thread has. */
    protected abstract fun currentThread(): String

    // Made only for the tests that use files.
    private val folder = lazy { TemporaryDirectory() }
    private val databaseFile by lazy { folder.value.path / BlueCardDatabase.NAME }
    private val databases = mutableListOf<BlueCardDatabase>()

    private fun open(
        builder: RoomDatabase.Builder<BlueCardDatabase>,
        driver: SQLiteDriver = BundledSQLiteDriver()
    ) = builder.setDriver(driver)
        .addColumnTypeConverter(Converters {})
        .build()
        .also { databases += it }

    // Lazy, as is repository, which uses it: a subclass's builders can use its own properties,
    // which aren't set yet while this class is constructed.
    private val database by lazy { open(inMemoryDatabaseBuilder()) }

    /** The first exception that would crash the app: a bug in a write. */
    private val crash = CompletableDeferred<Throwable>()

    // Lives as long as the test, as the app's does for the app. An exception that reaches it
    // would crash the app, so it's kept for the test to check.
    private val externalScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> crash.complete(e) }
    )

    private val start = BadgeStart(LocalDate(2026, 1, 1), LocalDate(2026, 4, 15))

    override val repository by lazy { RoomProgressRepository(database, externalScope) }

    override fun unreadableRepository() = unopenableRepository()

    override fun unwritableRepository() = unopenableRepository()

    // A folder where the database file should be, so SQLite can't open it.
    private fun unopenableRepository(): RoomProgressRepository {
        systemFileSystem.createDirectories(databaseFile)
        return RoomProgressRepository(open(databaseBuilder(databaseFile)), externalScope)
    }

    @Test
    fun readingAndWriting_whenRoomCantLockTheDatabase_throwIOException() = runTest {
        // A folder where Room's lock file goes, so Room can't create it.
        systemFileSystem.createDirectories("$databaseFile.lck".toPath())
        val repository = RoomProgressRepository(open(databaseBuilder(databaseFile)), externalScope)

        val readError = runCatching { repository.observeAllProgress().first() }.exceptionOrNull()
        val writeError = runCatching {
            repository.startBadge("archery", start.requirementsVersion, start.startedDate)
        }.exceptionOrNull()

        assertTrue(readError is IOException, "Expected an IOException, got $readError")
        assertTrue(writeError is IOException, "Expected an IOException, got $writeError")
    }

    @Test
    fun observing_whenMigrationIsMissing_throwsTheBugUnwrapped() = runTest {
        // A database from a newer app version, which Room has no migration down from.
        BundledSQLiteDriver().open(databaseFile.toString())
            .use { it.execSQL("PRAGMA user_version = 1000") }
        val repository = RoomProgressRepository(open(databaseBuilder(databaseFile)), externalScope)

        val error = runCatching { repository.observeAllProgress().first() }.exceptionOrNull()

        // A bug must crash, not become a load failure (see readFailuresAsIOException).
        assertTrue(
            error is IllegalStateException,
            "Expected Room's IllegalStateException, got $error"
        )
    }

    @Test
    fun changingARowWithoutADate_recordsTheDateItsChangedOn() = runTest {
        repository.startBadge("personal-management", start.requirementsVersion, start.startedDate)
        // Saved before database version 3, which records no date: a row of a fixed-row
        // tracker, and a log entry.
        val dao = database.progressDao()
        val week1 = dao.insertTrackerEntry(
            TrackerEntry(0, "personal-management", "2a", 1, mapOf("income" to "10"))
        )
        val session = dao.insertTrackerEntry(
            TrackerEntry(0, "personal-management", "9", null, mapOf("notes" to "Met"))
        )
        val today = LocalDate(2026, 9, 30)

        // Changed by its row, and by its entry's ID.
        repository.addTrackerEntry(
            "personal-management",
            "2a",
            1,
            mapOf("income" to "12"),
            today,
            start
        )
        repository.addTrackerEntry(
            "personal-management",
            "9",
            null,
            mapOf("notes" to "Met twice"),
            today,
            start,
            id = session
        )

        assertEquals(
            mapOf(week1 to today, session to today),
            repository.observeProgress("personal-management").first()!!
                .trackerEntries.associate { it.id to it.addedDate }
        )
    }

    /**
     * Makes every write of requirement progress fail, as a bug that breaks a constraint would.
     * SQLite reports a trigger's RAISE(ABORT) as a constraint violation:
     * https://www.sqlite.org/lang_createtrigger.html
     */
    private suspend fun failRequirementWrites() = database.useWriterConnection {
        it.executeSQL(
            """
            CREATE TRIGGER fail_requirement_writes BEFORE INSERT ON requirement_progress
            BEGIN SELECT RAISE(ABORT, 'Test failure'); END
            """
        )
    }

    private fun isConstraintViolation(error: Throwable?) =
        error is SQLiteException && error.resultCode == SQLITE_CONSTRAINT

    @Test
    fun recordingThatFailsPartWay_leavesTheBadgeUnstarted() = runTest {
        failRequirementWrites()
        val start = BadgeStart(LocalDate(2026, 1, 1), LocalDate(2026, 4, 15))

        runCatching { repository.markRequirementCompleted("archery", "1", null, start) }

        // The badge was started in the same transaction, so it's rolled back too.
        assertNull(repository.observeProgress("archery").first())
    }

    @Test
    fun replaceAllThatFailsPartWay_leavesProgressAsItWas() = runTest {
        repository.startBadge("archery", start.requirementsVersion, start.startedDate)
        repository.setRequirementSignOffAndComment("archery", "1", null, "Bows", start)
        val before = repository.observeAllProgress().first()
        failRequirementWrites()
        val camping = BadgeProgressDetails(
            start.progress("camping"),
            listOf(RequirementProgress("camping", "1", comment = "Tents")),
            emptyList()
        )

        runCatching { repository.replaceAll(listOf(camping)) }

        // Deleting the old progress and adding the badge were rolled back with it.
        assertEquals(before, repository.observeAllProgress().first())
    }

    @Test
    fun writing_thatBreaksAConstraint_throwsTheBugUnwrapped() = runTest {
        val start = BadgeStart(LocalDate(2026, 1, 1), LocalDate(2026, 3, 1))
        repository.startBadge("archery", start.requirementsVersion, start.startedDate)
        failRequirementWrites()

        val error = runCatching { repository.markRequirementCompleted("archery", "1", null, start) }
            .exceptionOrNull()

        // A bug must crash, not become a save failure (see isStorageFailure).
        assertTrue(isConstraintViolation(error), "Expected a constraint violation, got $error")
    }

    @Test
    fun writing_whenTheDiskIsFull_throwsIOException() = runTest {
        val full = SQLiteException("Error code: 13, message: database or disk is full")

        val error = runCatching { failingRequirementWrites(full).startAndRecord() }
            .exceptionOrNull()

        assertTrue(error is IOException, "Expected an IOException, got $error")
    }

    @Test
    fun writing_whenAnErrorHasNoResultCode_throwsTheBugUnwrapped() = runTest {
        // As Room throws for a failed foreign key check.
        val noCode = SQLiteException("Foreign key violation(s) detected in 'requirement_progress'.")

        val error = runCatching { failingRequirementWrites(noCode).startAndRecord() }
            .exceptionOrNull()

        assertEquals(noCode, error)
    }

    // replaceAll stores what it's given, so only Converters' escape keeps a lone surrogate from
    // being joined with the JSON's closing quote, which left the JSON unreadable.
    @Test
    fun replaceAll_trackerValueWithALoneSurrogate_readsBackAsGiven() = runTest {
        val values = mapOf("Notes" to "Trail \uD83D", "Place" to "\uDC22 camp 🏕")
        val entry = TrackerEntry(badgeId = "archery", requirementNumber = "9a", values = values)

        repository.replaceAll(
            listOf(BadgeProgressDetails(start.progress("archery"), emptyList(), listOf(entry)))
        )

        val stored = repository.observeProgress("archery").first()!!.trackerEntries.single()
        assertEquals(values, stored.values)
    }

    @Test
    fun writing_whenSomethingOtherThanSQLiteFails_throwsTheBugUnwrapped() = runTest {
        val bug = IllegalArgumentException("Test failure")

        val error = runCatching { failingRequirementWrites(bug).startAndRecord() }
            .exceptionOrNull()

        assertTrue(error is IllegalArgumentException, "Expected the bug, got $error")
    }

    @Test
    fun writing_whenCancelled_staysCancelled_evenIfCausedByAStorageFailure() = runTest {
        val cancelled = CancellationException("Cancelled", IOException())

        val error = runCatching { failingRequirementWrites(cancelled).startAndRecord() }
            .exceptionOrNull()

        assertTrue(error is CancellationException, "Expected the cancellation, got $error")
    }

    @Test
    fun writing_usesTheDatabaseOffTheCallersThread() = runTest {
        // Room runs queries on Dispatchers.IO by default, which externalScope runs on, as in the
        // app.
        val threads = Threads()
        val repository = RoomProgressRepository(
            open(inMemoryDatabaseBuilder(), ThreadRecordingDriver(threads)),
            externalScope
        )

        // One write of each kind: a single statement, and a transaction (afterStarting).
        repository.startBadge("archery", start.requirementsVersion, start.startedDate)
        repository.markRequirementCompleted("archery", "1", null, start)

        // A copy, since Room's refresh after a write may still be adding to it.
        val used = threads.toList()
        assertTrue(used.isNotEmpty())
        assertFalse(currentThread() in used, "Used on ${used.distinct()}")
    }

    /** The names of threads, added from any of them. */
    private class Threads {
        private val lock = Lock()
        private val names = mutableListOf<String>()

        fun add(name: String) = lock.withLock { names += name }

        fun toList() = lock.withLock { names.toList() }
    }

    /**
     * BundledSQLiteDriver, recording in [threads] the thread that opens each connection, which is
     * where the app's driver sets a damaged database aside, and that prepares each statement.
     */
    private inner class ThreadRecordingDriver(private val threads: Threads) : SQLiteDriver {
        private val driver = BundledSQLiteDriver()

        override fun open(fileName: String): SQLiteConnection {
            threads.add(currentThread())
            val connection = driver.open(fileName)
            return object : SQLiteConnection by connection {
                override fun prepare(sql: String): SQLiteStatement {
                    threads.add(currentThread())
                    return connection.prepare(sql)
                }
            }
        }
    }

    /** A repository whose writes of requirement progress throw [error]. */
    private fun failingRequirementWrites(error: RuntimeException) = RoomProgressRepository(
        open(inMemoryDatabaseBuilder(), FailingRequirementWrites(error)),
        externalScope
    )

    private suspend fun ProgressRepository.startAndRecord() {
        startBadge("archery", start.requirementsVersion, start.startedDate)
        markRequirementCompleted("archery", "1", null, start)
    }

    /** BundledSQLiteDriver, except that writes of requirement progress throw [error]. */
    private class FailingRequirementWrites(private val error: RuntimeException) : SQLiteDriver {
        private val driver = BundledSQLiteDriver()

        override fun open(fileName: String): SQLiteConnection {
            val connection = driver.open(fileName)
            return object : SQLiteConnection by connection {
                override fun prepare(sql: String): SQLiteStatement {
                    val statement = connection.prepare(sql)
                    if (!sql.startsWith("INSERT") || "`requirement_progress`" !in sql) {
                        return statement
                    }
                    return object : SQLiteStatement by statement {
                        override fun step(): Boolean = throw error
                    }
                }
            }
        }
    }

    /**
     * Runs [whileHeld] while another transaction holds the database, so the writes it starts
     * have to wait their turn, then lets them go.
     */
    private suspend fun TestScope.whileDatabaseIsHeld(whileHeld: suspend () -> Unit) {
        val holding = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val other = launch(Dispatchers.IO) {
            database.withWriteTransaction {
                holding.complete(Unit)
                release.await()
            }
        }
        holding.await()
        whileHeld()
        release.complete(Unit)
        other.join()
    }

    @Test
    fun write_finishesEvenIfItsCallerIsCancelled() = runTest(timeout = 10.seconds) {
        whileDatabaseIsHeld {
            // As when the scout saves and leaves the screen while the write waits.
            val caller = launch(start = CoroutineStart.UNDISPATCHED) {
                repository.markRequirementCompleted("archery", "1", null, start)
            }
            caller.cancel()
        }

        val progress = repository.observeProgress("archery").first { it != null }!!
        assertEquals(listOf("1"), progress.requirements.map { it.requirementNumber })
    }

    @Test
    fun writes_happenInTheOrderTheyreMade() = runTest(timeout = 10.seconds) {
        val dispatcher = HeldDispatcher()
        val repository =
            RoomProgressRepository(database, CoroutineScope(SupervisorJob() + dispatcher))

        val writes = (1..20).map { i ->
            launch(start = CoroutineStart.UNDISPATCHED) {
                repository.setRequirementSignOffAndComment("archery", "1", null, "Draft $i", start)
            }
        }
        dispatcher.release()
        writes.joinAll()

        val progress = repository.observeProgress("archery").first()!!
        assertEquals("Draft 20", progress.requirements.single().comment)
    }

    // Each screen with its own repository: the last write, made through the other one, waits
    // for those made before it, as a clear does for a save of notes made on another screen.
    @Test
    fun writeThroughAnotherRepository_waitsForTheWritesMadeBeforeIt() =
        runTest(timeout = 10.seconds) {
            val dispatcher = HeldDispatcher()
            val scope = CoroutineScope(SupervisorJob() + dispatcher)
            val (first, second) = List(2) { RoomProgressRepository(database, scope) }

            val writes = (1..20).map { i ->
                launch(start = CoroutineStart.UNDISPATCHED) {
                    (if (i < 20) first else second)
                        .setRequirementSignOffAndComment("archery", "1", null, "Draft $i", start)
                }
            }
            dispatcher.release()
            writes.joinAll()

            val progress = repository.observeProgress("archery").first()!!
            assertEquals("Draft 20", progress.requirements.single().comment)
        }

    /**
     * Holds the work dispatched to it until [release], then starts it newest first: the worst
     * order a thread pool could start it in. Work dispatched after that runs on the IO
     * dispatcher.
     */
    private class HeldDispatcher : CoroutineDispatcher() {
        private val lock = Lock()
        private var held: MutableList<Runnable>? = mutableListOf()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            val isHeld = lock.withLock { held?.add(block) != null }
            if (!isHeld) Dispatchers.IO.dispatch(context, block)
        }

        fun release() {
            val work = lock.withLock { held.also { held = null } }
            work!!.asReversed().forEach { it.run() }
        }
    }

    @Test
    fun bugInAWrite_crashesTheApp_evenIfItsCallerIsCancelled() = runTest(timeout = 10.seconds) {
        repository.startBadge("archery", start.requirementsVersion, start.startedDate)
        failRequirementWrites()

        whileDatabaseIsHeld {
            val caller = launch(start = CoroutineStart.UNDISPATCHED) {
                repository.markRequirementCompleted("archery", "1", null, start)
            }
            caller.cancel()
        }

        val error = crash.await()
        assertTrue(isConstraintViolation(error), "Expected a constraint violation, got $error")
    }

    @AfterTest
    fun closeDatabases() {
        try {
            externalScope.cancel()
            databases.forEach { it.close() }
        } finally {
            if (folder.isInitialized()) folder.value.delete()
        }
    }
}
