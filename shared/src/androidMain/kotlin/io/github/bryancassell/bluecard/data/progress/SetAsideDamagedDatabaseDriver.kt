package io.github.bryancassell.bluecard.data.progress

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteException
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Opens Room's database with [driver], except that a database SQLite finds damaged is set aside
 * ([FileDamagedProgressRepository.setAside]) rather than used (#70).
 *
 * The first time Room opens a connection in each app run, the database is checked on a
 * connection of its own, then set aside if it's damaged, so Room creates a new one in its place.
 * No connection has the files open while they move. Room opens its connections on its own
 * threads, never the main one.
 *
 * Damage found later, while reading or writing, is only recorded
 * ([FileDamagedProgressRepository.recordDamage]), since Room's other connections would keep
 * writing into the files if they were moved. The error goes on to the repository, which treats
 * it as a bug, so the app crashes, and the next run sets the database aside even if the check
 * passes. Otherwise damage the check misses would crash every run. Every statement after that
 * fails with the same error, so nothing more is saved into a database that will be set aside,
 * even where Room catches the first error, as its InvalidationTracker does (Room 3.0.3).
 *
 * [driver] is BundledSQLiteDriver. AndroidSQLiteDriver opens the database with Android's default
 * error handler, which deletes a damaged database and the files beside it, and takes no other
 * (checked in androidx.sqlite 2.7.1). Its SQLite calls use only androidx.sqlite's common
 * interfaces, but its lock, and FileDamagedProgressRepository's locks, Context and SQLiteDatabase,
 * need common replacements before iOS can use it (#345).
 */
class SetAsideDamagedDatabaseDriver(
    private val driver: SQLiteDriver,
    private val damagedProgress: FileDamagedProgressRepository
) : SQLiteDriver by driver {
    // Set once the database is known to be undamaged or is set aside, so a check that fails for
    // another reason, such as a full disk, runs again at the next open.
    private var checked = false

    // The damage found while the database is open, which every statement after it fails with.
    @Volatile
    private var damage: SQLiteException? = null

    override fun open(fileName: String): SQLiteConnection {
        setAsideIfDamaged(fileName)
        return DamageRecordingConnection(recordingDamage { driver.open(fileName).withLogLimits() })
    }

    /** The first time it's called, sets the database aside if it's damaged. */
    @Synchronized
    private fun setAsideIfDamaged(fileName: String) {
        if (checked) return
        val database = fileName.toPath()
        if (damagedProgress.isDamageRecorded(database) ||
            (FileSystem.SYSTEM.exists(database) && isDamaged(fileName))
        ) {
            damagedProgress.setAside(database)
        }
        checked = true
    }

    /**
     * Whether `PRAGMA quick_check` (https://www.sqlite.org/pragma.html#pragma_quick_check) finds
     * the database damaged. It reports what it finds as rows of text, and fails with
     * SQLITE_CORRUPT or SQLITE_NOTADB if it can't read the database's structure, such as in a
     * file that isn't a database. Any other failure says nothing about damage, so it's thrown.
     *
     * Pages that nothing uses ("Page 37: never used") only waste space: every read still works,
     * and Android's SQLite kept using such a database, so they don't count.
     *
     * Opening, checking and closing took about 20 ms for a 2.4 MB database and 60 ms for 26 MB,
     * on an Android 17 emulator with the file in the page cache (#339).
     */
    private fun isDamaged(fileName: String): Boolean = try {
        driver.open(fileName).use { connection ->
            connection.prepare("PRAGMA quick_check").use { check ->
                val problems = buildList { while (check.step()) add(check.getText(0)) }
                problems.any { it != "ok" && !UNUSED_PAGE.matches(it) }
            }
        }
    } catch (e: SQLiteException) {
        if (e.isDamage()) true else throw e
    }

    /**
     * Limits the write-ahead log as Android's SQLite does on each of its connections (its
     * db_journal_size_limit and db_wal_autocheckpoint settings). SQLite's defaults, which
     * BundledSQLiteDriver keeps, let the log grow to 1,000 pages before copying it into the
     * database, and never shrink it, so it would stay at its largest, and Auto Backup copies it.
     */
    private fun SQLiteConnection.withLogLimits() = apply {
        try {
            execSQL("PRAGMA journal_size_limit = 524288")
            execSQL("PRAGMA wal_autocheckpoint = 100")
        } catch (e: SQLiteException) {
            close()
            throw e
        }
    }

    private inline fun <T> recordingDamage(action: () -> T): T {
        damage?.let { throw it }
        return try {
            action()
        } catch (e: SQLiteException) {
            if (e.isDamage()) {
                damagedProgress.recordDamage()
                damage = e
            }
            throw e
        }
    }

    private inner class DamageRecordingConnection(private val connection: SQLiteConnection) :
        SQLiteConnection by connection {
        override fun prepare(sql: String): SQLiteStatement =
            DamageRecordingStatement(recordingDamage { connection.prepare(sql) })
    }

    private inner class DamageRecordingStatement(private val statement: SQLiteStatement) :
        SQLiteStatement by statement {
        override fun step() = recordingDamage { statement.step() }
    }
}

// SQLite 3.50 puts "*** in database main ***" on a line before the first problem it reports.
private val UNUSED_PAGE = Regex("""(\*\*\* in database main \*\*\*\n)?Page \d+: never used""")
