package io.github.bryancassell.bluecard.data.progress

import android.content.Context
import android.database.SQLException
import androidx.room3.Room
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.CancellationException
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Runs the repository contract against Room with an in-memory database, on the SQLite the app
 * bundles (BundledSQLiteDriver), under Robolectric as the other Room tests are. The Room testing
 * guide recommends plain JVM tests instead. Room 3's builders that take no Context would allow
 * one here too (tried with an in-memory database and the library's path set), and this test
 * becomes one when the database moves to common code (#345).
 */
@RunWith(AndroidJUnit4::class)
class RoomProgressRepositoryTest : ProgressRepositoryContract() {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databases = mutableListOf<BlueCardDatabase>()

    private fun open(
        builder: RoomDatabase.Builder<BlueCardDatabase>,
        driver: SQLiteDriver = BundledSQLiteDriver()
    ) = builder.setDriver(driver).build().also { databases += it }

    private val database = open(Room.inMemoryDatabaseBuilder(context, BlueCardDatabase::class.java))

    /** The first exception that would crash the app: a bug in a write. */
    private val crash = CompletableDeferred<Throwable>()

    // Lives as long as the test, as the app's does for the app. An exception that reaches it
    // would crash the app, so it's kept for the test to check.
    private val externalScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> crash.complete(e) }
    )

    private val start = BadgeStart(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 15))

    override val repository = RoomProgressRepository(database, externalScope)

    override fun unreadableRepository() = unopenableRepository()

    override fun unwritableRepository() = unopenableRepository()

    // A folder where the database file should be, so SQLite can't open it.
    private fun unopenableRepository() = RoomProgressRepository(
        open(
            Room.databaseBuilder(
                context,
                BlueCardDatabase::class.java,
                folder.newFolder(BlueCardDatabase.NAME).absolutePath
            )
        ),
        externalScope
    )

    @Test
    fun readingAndWriting_whenRoomCantLockTheDatabase_throwIOException() = runTest {
        // A folder where Room's lock file goes, so Room can't create it.
        val file = File(folder.root, BlueCardDatabase.NAME)
        File(file.path + ".lck").mkdirs()
        val repository = RoomProgressRepository(
            open(Room.databaseBuilder(context, BlueCardDatabase::class.java, file.absolutePath)),
            externalScope
        )

        val readError = runCatching { repository.observeAllProgress().first() }.exceptionOrNull()
        val writeError = runCatching {
            repository.startBadge("archery", start.requirementsVersion, start.startedDate)
        }.exceptionOrNull()

        assertTrue("Expected an IOException, got $readError", readError is IOException)
        assertTrue("Expected an IOException, got $writeError", writeError is IOException)
    }

    @Test
    fun observing_whenMigrationIsMissing_throwsTheBugUnwrapped() = runTest {
        // A database from a newer app version, which Room has no migration down from.
        val file = File(folder.root, BlueCardDatabase.NAME)
        BundledSQLiteDriver().open(file.path).use { it.execSQL("PRAGMA user_version = 1000") }
        val repository = RoomProgressRepository(
            open(Room.databaseBuilder(context, BlueCardDatabase::class.java, file.absolutePath)),
            externalScope
        )

        val error = runCatching { repository.observeAllProgress().first() }.exceptionOrNull()

        // A bug must crash, not become a load failure (see readFailuresAsIOException).
        assertTrue(
            "Expected Room's IllegalStateException, got $error",
            error is IllegalStateException
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
        val today = LocalDate.of(2026, 9, 30)

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
        val start = BadgeStart(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 15))

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
        val start = BadgeStart(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1))
        repository.startBadge("archery", start.requirementsVersion, start.startedDate)
        failRequirementWrites()

        val error = runCatching { repository.markRequirementCompleted("archery", "1", null, start) }
            .exceptionOrNull()

        // A bug must crash, not become a save failure (see isStorageFailure).
        assertTrue("Expected a constraint violation, got $error", isConstraintViolation(error))
    }

    @Test
    fun writing_whenTheDiskIsFull_throwsIOException() = runTest {
        val full = SQLException("Error code: 13, message: database or disk is full")

        val error = runCatching { failingRequirementWrites(full).startAndRecord() }
            .exceptionOrNull()

        assertTrue("Expected an IOException, got $error", error is IOException)
    }

    @Test
    fun writing_whenAnErrorHasNoResultCode_throwsTheBugUnwrapped() = runTest {
        // As Room throws for a failed foreign key check.
        val noCode = SQLException("Foreign key violation(s) detected in 'requirement_progress'.")

        val error = runCatching { failingRequirementWrites(noCode).startAndRecord() }
            .exceptionOrNull()

        assertEquals(noCode, error)
    }

    @Test
    fun writing_whenSomethingOtherThanSQLiteFails_throwsTheBugUnwrapped() = runTest {
        val bug = IllegalArgumentException("Test failure")

        val error = runCatching { failingRequirementWrites(bug).startAndRecord() }
            .exceptionOrNull()

        assertTrue("Expected the bug, got $error", error is IllegalArgumentException)
    }

    @Test
    fun writing_whenCancelled_staysCancelled_evenIfCausedByAStorageFailure() = runTest {
        val cancelled = CancellationException("Cancelled").apply { initCause(IOException()) }

        val error = runCatching { failingRequirementWrites(cancelled).startAndRecord() }
            .exceptionOrNull()

        assertTrue("Expected the cancellation, got $error", error is CancellationException)
    }

    /** A repository whose writes of requirement progress throw [error]. */
    private fun failingRequirementWrites(error: RuntimeException) = RoomProgressRepository(
        open(
            Room.inMemoryDatabaseBuilder(context, BlueCardDatabase::class.java),
            FailingRequirementWrites(error)
        ),
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

    /**
     * Holds the work dispatched to it until [release], then starts it newest first: the worst
     * order a thread pool could start it in. Work dispatched after that runs on the IO
     * dispatcher.
     */
    private class HeldDispatcher : CoroutineDispatcher() {
        private var held: MutableList<Runnable>? = mutableListOf()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            synchronized(this) {
                held?.let {
                    it += block
                    return
                }
            }
            Dispatchers.IO.dispatch(context, block)
        }

        fun release() {
            val work = synchronized(this) { held.also { held = null } }
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
        assertTrue("Expected a constraint violation, got $error", isConstraintViolation(error))
    }

    @After
    fun closeDatabases() {
        externalScope.cancel()
        databases.forEach { it.close() }
    }
}
