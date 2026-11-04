package io.github.bryancassell.bluecard.data.progress

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteException
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import io.github.bryancassell.bluecard.data.Lock
import io.github.bryancassell.bluecard.data.systemFileSystem
import kotlin.concurrent.Volatile
import okio.Path.Companion.toPath

/**
 * Opens Room's database with [driver], except that a damaged database is set aside
 * ([FileDamagedProgressRepository.setAside]) rather than used (#70).
 *
 * The first time Room opens a connection in each app run, the database is checked on a
 * connection of its own, then set aside if it's damaged, so Room creates a new one in its place.
 * No connection has the files open while they move. Room opens its connections on its own
 * threads, never the main one.
 *
 * Damage found later, while reading or writing, is only recorded
 * ([FileDamagedProgressRepository.recordDamage]), since Room's other connections would keep
 * writing into the files if they were moved. That includes a stored value [Converters] can't
 * read ([recordUnreadableValue]). The error goes on to the repository, which treats
 * it as a bug, so the app crashes, and the next run sets the database aside even if the check
 * passes, unless the database is written after the record
 * ([FileDamagedProgressRepository.isDamageRecorded]). Otherwise damage the check misses would
 * crash every run. Every statement after that fails with the damage error, so nothing more is
 * saved into a database that will be set aside, even where Room catches the first error, as its
 * InvalidationTracker does (Room 3.0.3).
 *
 * On Android, [driver] is BundledSQLiteDriver. AndroidSQLiteDriver opens the database with
 * Android's default error handler, which deletes a damaged database and the files beside it, and
 * takes no other (checked in androidx.sqlite 2.7.1).
 */
class SetAsideDamagedDatabaseDriver(
    private val driver: SQLiteDriver,
    private val damagedProgress: FileDamagedProgressRepository
) : SQLiteDriver by driver {
    private val lock = Lock()

    // Set once the database is known to be undamaged, or is set aside or recorded as damaged, so a
    // check that fails for another reason, such as a full disk, runs again at the next open.
    private var checked = false

    // The damage found while the database is open, which every statement after it fails with.
    @Volatile
    private var damage: SQLiteException? = null

    override fun open(fileName: String): SQLiteConnection {
        setAsideIfDamaged(fileName)
        return DamageRecordingConnection(recordingDamage { driver.open(fileName).withLogLimits() })
    }

    /** The first time it's called, sets the database aside if it's damaged. */
    private fun setAsideIfDamaged(fileName: String) = lock.withLock {
        if (checked) return@withLock
        val database = fileName.toPath()
        // A record older than the database was left by a set-aside that couldn't delete it.
        // Cleared first, so damage the check finds now gets a record of its own, which the next
        // launch needs if the app stops partway through setting it aside.
        damagedProgress.clearLeftoverDamageRecord(database)
        if (damagedProgress.isDamageRecorded(database) ||
            (systemFileSystem.exists(database) && isDamaged(fileName))
        ) {
            // One that can be neither moved nor deleted is handled as damage found while it's
            // open, so nothing more is saved into it, and the next launch tries again.
            if (!damagedProgress.setAside(database)) {
                record(SQLiteException("Error code: $SQLITE_CORRUPT, message: not set aside"))
            }
        }
        checked = true
    }

    /**
     * Whether `PRAGMA quick_check` (https://www.sqlite.org/pragma.html#pragma_quick_check) finds
     * the database damaged, or Room's own values in it are ([hasDamagedRoomHeader]). The check
     * reports what it finds as rows of text, and fails with SQLITE_CORRUPT or SQLITE_NOTADB if it
     * can't read the database's structure, such as in a file that isn't a database. Any other
     * failure says nothing about damage, so it's thrown.
     *
     * Pages that nothing uses ("Page 37: never used") only waste space: every read still works,
     * and Android's SQLite kept using such a database, so they don't count.
     *
     * Opening, checking and closing took about 20 ms for a 2.4 MB database and 60 ms for 26 MB,
     * on an Android 17 emulator with the file in the page cache (#339).
     */
    private fun isDamaged(fileName: String): Boolean = try {
        driver.open(fileName).use { connection ->
            val problems = connection.prepare("PRAGMA quick_check").use { check ->
                buildList { while (check.step()) add(check.getText(0)) }
            }
            problems.any { it != "ok" && !UNUSED_PAGE.matches(it) } ||
                connection.hasDamagedRoomHeader()
        }
    } catch (e: SQLiteException) {
        if (e.isDamage()) true else throw e
    }

    /**
     * Whether Room's schema version (the file's `user_version`) and its identity hash (in
     * `room_master_table`) disagree on whether the database has this build's schema. Storage
     * can change either without `quick_check` noticing, since SQLite doesn't use them, and Room
     * then refuses the database at every launch (#389). With this build's version, a changed or
     * missing hash fails Room's identity check (`checkIdentity`, Room 3.0.3). With this build's
     * hash, a higher version has no migration, and a lower one runs migrations on a schema that
     * already has their columns, which fails with SQLITE_ERROR "duplicate column name", a load
     * failure.
     *
     * Left to Room, which keeps the progress or refuses it as before:
     * - Version 0. Room's create path keeps the tables it finds if they're this schema's, and
     *   saves the version and hash again.
     * - No `room_master_table`. Room's identity check then checks the tables in the same way,
     *   and saves the hash again.
     * - Neither is this build's. That's an older database, which Room migrates, or a newer
     *   build's, which it refuses with a missing migration, a crash on purpose: reinstalling the
     *   newer build opens it again, and setting it aside would tell the scout it's damaged. A
     *   newer version that kept this schema would have this hash, and would be set aside.
     *
     * Room also accepts a "legacy" hash, from an older way of computing it. No database here has
     * one: every Room version the app has used computed this hash for this schema, as version
     * 4's file in shared/schemas/ shows, which CI fails the build for changing.
     */
    private fun SQLiteConnection.hasDamagedRoomHeader(): Boolean {
        val version = prepare("PRAGMA user_version").use {
            it.step()
            it.getLong(0)
        }
        if (version == 0L) return false
        val hasIdentityTable = prepare(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'room_master_table'"
        ).use { it.step() }
        if (!hasIdentityTable) return false
        // Room's own query, from RoomMasterTable, which it keeps internal.
        val identityHash = prepare(
            "SELECT identity_hash FROM room_master_table WHERE id = 42 LIMIT 1"
        ).use { if (it.step() && !it.isNull(0)) it.getText(0) else null }
        return (version == BlueCardDatabase.VERSION.toLong()) !=
            (identityHash == BlueCardDatabase.IDENTITY_HASH)
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

    /**
     * Records [error], from a stored value [Converters] couldn't read, as damage found while the
     * database is open. `PRAGMA quick_check` doesn't find it, so the record is what sets the
     * database aside at the next launch.
     *
     * Later statements fail with SQLITE_CORRUPT, as for damage SQLite finds, caused by [error].
     * Room's InvalidationTracker catches only a SQLiteException (`notifyInvalidation`, Room
     * 3.0.3), so [error] itself would crash the app from Room's own coroutine scope as well.
     */
    fun recordUnreadableValue(error: IllegalArgumentException) =
        record(sqliteException("Error code: $SQLITE_CORRUPT, message: unreadable value", error))

    private inline fun <T> recordingDamage(action: () -> T): T {
        damage?.let { throw it }
        return try {
            action()
        } catch (e: SQLiteException) {
            if (e.isDamage()) record(e)
            throw e
        }
    }

    // Statements stop first, so no commit starts while the record is written, which a checkpoint
    // could leave the database newer than (isDamageRecorded).
    private fun record(error: SQLiteException) {
        damage = error
        damagedProgress.recordDamage()
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
