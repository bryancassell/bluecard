package io.github.bryancassell.bluecard.data.progress

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
 * Runs the repository contract against Room with an in-memory database. Robolectric
 * provides the Context that Room needs on Android (see ARCHITECTURE.md, Testing approach).
 */
@RunWith(AndroidJUnit4::class)
class RoomProgressRepositoryTest : ProgressRepositoryContract() {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databases = mutableListOf<BlueCardDatabase>()

    private fun open(builder: RoomDatabase.Builder<BlueCardDatabase>) =
        builder.build().also { databases += it }

    private val database = open(Room.inMemoryDatabaseBuilder(context, BlueCardDatabase::class.java))

    // Lives as long as the test, as the app's does for the app.
    private val externalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
    fun observing_whenMigrationIsMissing_throwsTheBugUnwrapped() = runTest {
        // A database from a newer app version, which Room has no migration down from.
        val file = File(folder.root, BlueCardDatabase.NAME)
        SQLiteDatabase.openOrCreateDatabase(file, null).use { it.version = 1_000 }
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

    /**
     * Makes every write of requirement progress fail, as a bug that breaks a constraint would.
     * SQLite reports a trigger's RAISE(ABORT) as a constraint violation:
     * https://www.sqlite.org/lang_createtrigger.html
     */
    private fun failRequirementWrites() {
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_requirement_writes BEFORE INSERT ON requirement_progress
            BEGIN SELECT RAISE(ABORT, 'Test failure'); END
            """
        )
    }

    @Test
    fun recordingThatFailsPartWay_leavesTheBadgeUnstarted() = runTest {
        failRequirementWrites()
        val start = BadgeStart(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 15))

        runCatching { repository.markRequirementCompleted("archery", "1", null, start) }

        // The badge was started in the same transaction, so it's rolled back too.
        assertNull(repository.observeProgress("archery").first())
    }

    @Test
    fun writing_thatBreaksAConstraint_throwsTheBugUnwrapped() = runTest {
        repository.startBadge("archery", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1))
        failRequirementWrites()

        val error = runCatching { repository.markRequirementCompleted("archery", "1", null) }
            .exceptionOrNull()

        // A bug must crash, not become a save failure (see isStorageFailure).
        assertTrue(
            "Expected SQLiteConstraintException, got $error",
            error is SQLiteConstraintException
        )
    }

    @Test
    fun write_finishesEvenIfItsCallerIsCancelled() = runTest(timeout = 10.seconds) {
        // Another transaction holds the database, so the write has to wait its turn.
        val holding = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val other = launch(Dispatchers.IO) {
            database.withTransaction {
                holding.complete(Unit)
                release.await()
            }
        }
        holding.await()
        val start = BadgeStart(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 15))

        // As when the scout saves and leaves the screen while the write waits.
        val caller = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.markRequirementCompleted("archery", "1", null, start)
        }
        caller.cancel()
        release.complete(Unit)
        other.join()

        val progress = repository.observeProgress("archery").first { it != null }!!
        assertEquals(listOf("1"), progress.requirements.map { it.requirementNumber })
    }

    @After
    fun closeDatabases() {
        externalScope.cancel()
        databases.forEach { it.close() }
    }
}
