package io.github.bryancassell.bluecard.data.progress

import android.content.Context
import android.database.SQLException
import androidx.room3.Room
import androidx.room3.testing.MigrationTestHelper
import androidx.room3.useReaderConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteException
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bryancassell.bluecard.di.DeviceClock
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
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
        DeviceClock,
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
        val database = open(driver)
        val repository =
            RoomProgressRepository(
                database,
                CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            )

        val error = runCatching { repository.observeAllProgress().first() }.exceptionOrNull()

        // A bug, which crashes the app, rather than a load failure (IOException).
        assertTrue(
            "Expected SQLite to find the damage, got $error",
            error is SQLiteException && error.isDamage()
        )
        // Even a statement that reads nothing fails after that, so nothing more is saved.
        val next = runCatching {
            database.useReaderConnection { it.usePrepared("SELECT 1") { select -> select.step() } }
        }.exceptionOrNull()
        assertTrue(
            "Expected the same error, got $next",
            next is SQLiteException && next.message == error?.message
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
        saveProgress(database)
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

    /** Saves enough badges to fill several pages, then closes [database]. */
    private suspend fun saveProgress(database: BlueCardDatabase) {
        repeat(300) {
            database.progressDao().insertBadge(
                BadgeProgress("badge-$it-${"x".repeat(200)}", VERSION, LocalDate(2026, 3, 1))
            )
        }
        database.close()
    }

    private fun SQLiteConnection.longFor(sql: String) = prepare(sql).use {
        it.step()
        it.getLong(0)
    }

    @Test
    fun databaseWithAPageNothingUses_staysInUse() = runTest {
        saveProgress(open())
        addPageNothingUses()
        val check = BundledSQLiteDriver().open(file.path).use { sqlite ->
            sqlite.prepare("PRAGMA quick_check").use {
                it.step()
                it.getText(0)
            }
        }
        assertTrue(check, check.endsWith(": never used"))

        val progress = open().readProgress()

        assertEquals(300, progress.size)
        assertEquals(emptyList<File>(), copies())
        assertFalse(damagedProgress.observeNoticePending().first())
    }

    /**
     * Adds a page to the end of the database and counts it in the database's size, with nothing
     * using it, as when a page leaks. SQLite trusts the size in the header
     * (https://www.sqlite.org/fileformat2.html#in_header_database_size).
     */
    private fun addPageNothingUses() = RandomAccessFile(file, "rw").use {
        it.seek(16)
        val pageSize = it.readUnsignedShort().let { size -> if (size == 1) 65536 else size }
        it.seek(28)
        val pages = it.readInt()
        it.setLength((pages + 1L) * pageSize)
        it.seek(28)
        it.writeInt(pages + 1)
    }

    @Test
    fun connectionWhoseLogLimitsFail_isClosed_andTheErrorGoesOn() {
        val full = SQLException("Error code: 13, message: database or disk is full")
        var closed = false
        val failing = object : SQLiteDriver {
            override fun open(fileName: String) = object : SQLiteConnection {
                override fun prepare(sql: String): SQLiteStatement = throw full

                override fun close() {
                    closed = true
                }
            }
        }

        val error = runCatching {
            SetAsideDamagedDatabaseDriver(failing, damagedProgress).open(file.path)
        }.exceptionOrNull()

        assertEquals(full, error)
        assertTrue(closed)
    }

    @Test
    fun connections_limitTheWriteAheadLog_asAndroidsSQLiteDoes() {
        file.parentFile!!.mkdirs()

        newDriver().open(file.path).use {
            assertEquals(524288L, it.longFor("PRAGMA journal_size_limit"))
            assertEquals(100L, it.longFor("PRAGMA wal_autocheckpoint"))
        }
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

        // Room tries again at the next read.
        file.delete()
        val damaged = ByteArray(8192) { 7 }
        file.writeBytes(damaged)
        val progress = repository.observeAllProgress().first()

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
        val VERSION: LocalDate = LocalDate(2026, 1, 1)
    }
}
