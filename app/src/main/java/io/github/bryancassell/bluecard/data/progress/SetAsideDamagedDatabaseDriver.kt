package io.github.bryancassell.bluecard.data.progress

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteException
import androidx.sqlite.SQLiteStatement
import java.io.File

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
 * passes. Otherwise damage the check misses would crash every run.
 *
 * [driver] is BundledSQLiteDriver. AndroidSQLiteDriver opens the database with Android's default
 * error handler, which deletes a damaged database and the files beside it, and takes no other
 * (checked in androidx.sqlite 2.6.2). This uses only androidx.sqlite's common interfaces, so iOS
 * can use it too (#345).
 */
class SetAsideDamagedDatabaseDriver(
    private val driver: SQLiteDriver,
    private val damagedProgress: FileDamagedProgressRepository
) : SQLiteDriver by driver {
    // Set once the database is known to be undamaged or is set aside, so a check that fails for
    // another reason, such as a full disk, runs again at the next open.
    private var checked = false

    override fun open(fileName: String): SQLiteConnection {
        setAsideIfDamaged(fileName)
        return DamageRecordingConnection(recordingDamage { driver.open(fileName) })
    }

    /** The first time it's called, sets the database aside if it's damaged. */
    @Synchronized
    private fun setAsideIfDamaged(fileName: String) {
        if (checked) return
        val database = File(fileName)
        if (damagedProgress.isDamageRecorded() || (database.exists() && isDamaged(fileName))) {
            damagedProgress.setAside(database)
        }
        checked = true
    }

    /**
     * Whether `PRAGMA quick_check` (https://www.sqlite.org/pragma.html#pragma_quick_check) finds
     * the database damaged. It reports what it finds as rows of text, and fails with
     * SQLITE_CORRUPT or SQLITE_NOTADB if it can't read the database's structure, such as in a
     * file that isn't a database. Any other failure says nothing about damage, so it's thrown.
     * Opening, checking and closing took about 20 ms for a 2.4 MB database and 60 ms for 26 MB,
     * on an Android 17 emulator (#339).
     */
    private fun isDamaged(fileName: String): Boolean = try {
        driver.open(fileName).use { connection ->
            connection.prepare("PRAGMA quick_check").use { it.step() && it.getText(0) != "ok" }
        }
    } catch (e: SQLiteException) {
        if (e.isDamage()) true else throw e
    }

    private inline fun <T> recordingDamage(action: () -> T): T = try {
        action()
    } catch (e: SQLiteException) {
        if (e.isDamage()) damagedProgress.recordDamage()
        throw e
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
