package io.github.bryancassell.bluecard.data.progress

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens the database as the app does, on files damaged as SQLite finds them, with the SQLite the
 * app bundles.
 */
@RunWith(AndroidJUnit4::class)
class SetAsideDamagedDatabaseDriverTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = context.getDatabasePath(BlueCardDatabase.NAME)

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        file,
        BundledSQLiteDriver(),
        BlueCardDatabase::class
    )

    private val damagedProgress = FileDamagedProgressRepository(
        context,
        Clock.systemUTC(),
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        Dispatchers.Unconfined
    )
    private val databases = mutableListOf<BlueCardDatabase>()

    @After
    fun closeDatabases() {
        databases.forEach { it.close() }
    }

    /** The driver as a new app run gets it. */
    private fun newDriver() = SetAsideDamagedDatabaseDriver(BundledSQLiteDriver(), damagedProgress)

    private fun open(driver: SQLiteDriver = newDriver()) =
        Room.databaseBuilder(context, BlueCardDatabase::class.java, BlueCardDatabase.NAME)
            .setDriver(driver)
            .build()
            .also { databases += it }

    private suspend fun BlueCardDatabase.readProgress() = progressDao().observeAll().first()

    private val folder = File(context.noBackupFilesDir, FileDamagedProgressRepository.FOLDER_NAME)

    /** The folders progress was set aside in. */
    private fun copies() = folder.listFiles { file -> file.isDirectory }.orEmpty().toList()

    /** The database file set aside, the only one in its folder's only copy. */
    private fun setAside() = copies().single().resolve(BlueCardDatabase.NAME)

    @Test
    fun databaseDamagedBeforeItOpens_isSetAside_andAnEmptyOneOpens() = runTest {
        file.parentFile!!.mkdirs()
        val damaged = ByteArray(8192) { 7 }
        file.writeBytes(damaged)

        val progress = open().readProgress()

        assertEquals(emptyList<BadgeProgressDetails>(), progress)
        assertArrayEquals(damaged, setAside().readBytes())
        assertTrue(damagedProgress.observeNoticePending().first())
    }

    @Test
    fun databaseWithDamagedPages_isSetAsideWhenItOpens_andAnEmptyOneOpens() = runTest {
        saveProgressAndDamageIt()
        val damaged = file.readBytes()

        val progress = open().readProgress()

        assertEquals(emptyList<BadgeProgressDetails>(), progress)
        assertArrayEquals(damaged, setAside().readBytes())
        assertTrue(damagedProgress.observeNoticePending().first())
    }

    @Test
    fun damageFoundWhileReading_isSetAsideAtTheNextLaunch_evenIfTheCheckPasses() = runTest {
        // One driver for two databases, so the second skips the check, as a database damaged
        // after it's checked would.
        val driver = newDriver()
        val undamaged = saveProgressAndDamageIt(open(driver))
        val repository = RoomProgressRepository(
            open(driver),
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )

        val error = runCatching { repository.observeAllProgress().first() }.exceptionOrNull()

        // A bug, which crashes the app, rather than a load failure (IOException).
        assertTrue(
            "Expected SQLite to find the damage, got $error",
            error is SQLiteException && error.isDamage()
        )
        assertTrue(damagedProgress.isDamageRecorded())
        assertEquals(emptyList<File>(), copies())
        assertFalse(damagedProgress.observeNoticePending().first())

        // The next launch, with damage the check doesn't find.
        databases.last().close()
        file.writeBytes(undamaged)
        val progress = open().readProgress()

        assertEquals(emptyList<BadgeProgressDetails>(), progress)
        assertArrayEquals(undamaged, setAside().readBytes())
        assertTrue(damagedProgress.observeNoticePending().first())
        assertFalse(damagedProgress.isDamageRecorded())
    }

    /**
     * Saves enough badges to fill several pages, then damages every page after the tables'
     * first ones. SQLite reads only those first pages to open the database, so it opens, and
     * finds the damage when progress is read. Gives the file as it was before the damage.
     */
    private suspend fun saveProgressAndDamageIt(database: BlueCardDatabase = open()): ByteArray {
        repeat(300) {
            database.progressDao().insertBadge(
                BadgeProgress("badge-$it-${"x".repeat(200)}", VERSION, LocalDate.of(2026, 3, 1))
            )
        }
        database.close()
        val (pageSize, lastTablePage) = BundledSQLiteDriver().open(file.path).use { sqlite ->
            sqlite.longFor("PRAGMA page_size") to
                sqlite.longFor("SELECT max(rootpage) FROM sqlite_master")
        }
        val undamaged = file.readBytes()
        RandomAccessFile(file, "rw").use {
            val firstDamaged = lastTablePage * pageSize
            assertTrue("Expected pages after the tables' first ones", it.length() > firstDamaged)
            it.seek(firstDamaged)
            it.write(ByteArray((it.length() - firstDamaged).toInt()) { 0x55 })
        }
        return undamaged
    }

    private fun SQLiteConnection.longFor(sql: String) = prepare(sql).use {
        it.step()
        it.getLong(0)
    }

    @Test
    fun checkThatFailsForAnotherReason_movesNothing_andRunsAgainAtTheNextOpen() = runTest {
        // A folder where the database file should be, so SQLite can't open it.
        file.mkdirs()
        val driver = newDriver()
        val repository = RoomProgressRepository(
            open(driver),
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )

        val error = runCatching { repository.observeAllProgress().first() }.exceptionOrNull()

        // A load failure, as for a full disk.
        assertTrue("Expected an IOException, got $error", error is IOException)
        assertTrue(file.isDirectory)
        assertEquals(emptyList<File>(), copies())
        assertFalse(damagedProgress.observeNoticePending().first())

        file.delete()
        val damaged = ByteArray(8192) { 7 }
        file.writeBytes(damaged)
        val progress = open(driver).readProgress()

        assertEquals(emptyList<BadgeProgressDetails>(), progress)
        assertArrayEquals(damaged, setAside().readBytes())
    }

    @Test
    fun olderDatabase_isMigratedByRoom() = runTest {
        helper.createDatabase(1).use {
            it.execSQL(
                """
                INSERT INTO badge_progress (badgeId, requirementsVersion, startedDate)
                VALUES ('camping', '2026-01-01', '2026-03-01')
                """
            )
        }

        val progress = open().readProgress()

        assertEquals(listOf("camping"), progress.map { it.badge.badgeId })
    }

    @Test
    fun newerDatabase_isRefusedByRoom() = runTest {
        open().readProgress()
        databases.single().close()
        BundledSQLiteDriver().open(file.path).use { it.execSQL("PRAGMA user_version = 99") }

        val error = runCatching { open().readProgress() }.exceptionOrNull()

        // Room's error for a missing migration.
        assertTrue(
            "Expected Room's missing migration error, got $error",
            error is IllegalStateException
        )
    }

    private companion object {
        val VERSION: LocalDate = LocalDate.of(2026, 1, 1)
    }
}
