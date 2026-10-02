package io.github.bryancassell.bluecard.data.progress

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.RandomAccessFile
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens the database as the app does, on files damaged as SQLite finds them. Robolectric runs
 * Android's SQLite code, which calls the corruption handler as on a phone.
 */
@RunWith(AndroidJUnit4::class)
class SetAsideDamagedDatabaseFactoryTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BlueCardDatabase::class.java
    )

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = context.getDatabasePath(BlueCardDatabase.NAME)
    private val setAside = File(
        File(context.noBackupFilesDir, FileDamagedProgressRepository.FOLDER_NAME),
        BlueCardDatabase.NAME
    )
    private val damagedProgress = FileDamagedProgressRepository(context, Dispatchers.Unconfined)
    private val databases = mutableListOf<BlueCardDatabase>()

    @After
    fun closeDatabases() {
        databases.forEach { it.close() }
    }

    /** Opens the database as a new app process would. */
    private fun open() =
        Room.databaseBuilder(context, BlueCardDatabase::class.java, BlueCardDatabase.NAME)
            .openHelperFactory(SetAsideDamagedDatabaseFactory(damagedProgress))
            .build()
            .also { databases += it }

    private suspend fun BlueCardDatabase.readProgress() = progressDao().observeAll().first()

    @Test
    fun databaseDamagedBeforeItOpens_isSetAside_andAnEmptyOneOpens() = runTest {
        file.parentFile!!.mkdirs()
        val damaged = ByteArray(8192) { 7 }
        file.writeBytes(damaged)

        val progress = open().readProgress()

        assertEquals(emptyList<BadgeProgressDetails>(), progress)
        assertArrayEquals(damaged, setAside.readBytes())
        assertTrue(damagedProgress.observeNoticePending().first())
    }

    @Test
    fun databaseDamagedWhileReading_isSetAside_andAnEmptyOneOpensNextTime() = runTest {
        saveProgressAndDamageIt()
        val damaged = file.readBytes()

        val error = runCatching { open().readProgress() }.exceptionOrNull()

        assertTrue(
            "Expected the damage to be found, got $error",
            error is SQLiteDatabaseCorruptException
        )
        assertArrayEquals(damaged, setAside.readBytes())
        assertTrue(damagedProgress.observeNoticePending().first())
        assertEquals(emptyList<BadgeProgressDetails>(), open().readProgress())
    }

    /**
     * Saves enough badges to fill several pages, then damages every page after the tables'
     * first ones. SQLite reads only those first pages to open the database, so it opens, and
     * finds the damage when progress is read.
     */
    private suspend fun saveProgressAndDamageIt() {
        val database = open()
        repeat(300) {
            database.progressDao().insertBadge(
                BadgeProgress("badge-$it-${"x".repeat(200)}", VERSION, LocalDate.of(2026, 3, 1))
            )
        }
        val sqlite = database.openHelper.writableDatabase
        val pageSize = sqlite.longFor("PRAGMA page_size")
        val lastTablePage = sqlite.longFor("SELECT max(rootpage) FROM sqlite_master")
        database.close()
        RandomAccessFile(file, "rw").use {
            val firstDamaged = lastTablePage * pageSize
            assertTrue("Expected pages after the tables' first ones", it.length() > firstDamaged)
            it.seek(firstDamaged)
            it.write(ByteArray((it.length() - firstDamaged).toInt()) { 0x55 })
        }
    }

    private fun SupportSQLiteDatabase.longFor(sql: String) = query(sql).use {
        it.moveToFirst()
        it.getLong(0)
    }

    @Test
    fun olderDatabase_isMigratedByRoom() = runTest {
        helper.createDatabase(BlueCardDatabase.NAME, 1).use {
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
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.version = 99
        }

        val error = runCatching { open().readProgress() }.exceptionOrNull()

        // Room's error for a missing migration. Android's default downgrade handler throws a
        // SQLiteException instead.
        assertTrue(
            "Expected Room's missing migration error, got $error",
            error is IllegalStateException
        )
    }

    private companion object {
        val VERSION: LocalDate = LocalDate.of(2026, 1, 1)
    }
}
