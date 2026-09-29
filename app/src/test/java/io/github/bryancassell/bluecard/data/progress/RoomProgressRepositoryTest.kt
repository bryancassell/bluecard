package io.github.bryancassell.bluecard.data.progress

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
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

    override val repository = RoomProgressRepository(database)

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
        )
    )

    @Test
    fun observing_whenMigrationIsMissing_throwsTheBugUnwrapped() = runTest {
        // A database from a newer app version, which Room has no migration down from.
        val file = File(folder.root, BlueCardDatabase.NAME)
        SQLiteDatabase.openOrCreateDatabase(file, null).use { it.version = 1_000 }
        val repository = RoomProgressRepository(
            open(Room.databaseBuilder(context, BlueCardDatabase::class.java, file.absolutePath))
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

    @After
    fun closeDatabases() {
        databases.forEach { it.close() }
    }
}
