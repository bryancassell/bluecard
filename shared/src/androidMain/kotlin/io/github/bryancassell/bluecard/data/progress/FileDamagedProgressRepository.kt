package io.github.bryancassell.bluecard.data.progress

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.github.bryancassell.bluecard.data.deleteOrWarn
import io.github.bryancassell.bluecard.data.runOutlivingCaller
import io.github.bryancassell.bluecard.di.ApplicationScope
import io.github.bryancassell.bluecard.di.IoDispatcher
import io.github.bryancassell.bluecard.di.WarningLog
import io.github.bryancassell.bluecard.di.ZonedClock
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath

/**
 * [DamagedProgressRepository] that keeps each damaged database in a folder of its own, in the
 * app's no-backup directory, with a file there while the scout hasn't been told. Auto Backup
 * skips that directory, so a phone restored from a backup gets neither the damaged copies nor
 * a notice about progress it never had.
 *
 * A singleton, so the notice shows as soon as [setAside] runs, without the app reopening. The
 * notice file and [noticePending] change together, under this object's lock.
 */
class FileDamagedProgressRepository(
    private val context: Context,
    private val clock: ZonedClock,
    private val warningLog: WarningLog,
    @param:ApplicationScope private val externalScope: CoroutineScope,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : DamagedProgressRepository {
    private val fileSystem = FileSystem.SYSTEM

    // Found when first used, off the main thread: finding the no-backup directory creates it.
    private val folder by lazy { context.noBackupFilesDir.toOkioPath() / FOLDER_NAME }
    private val noticeFile by lazy { folder / NOTICE_FILE_NAME }
    private val damageFile by lazy { folder / DAMAGE_FILE_NAME }

    // Null until noticeFile is first checked.
    private val noticePending = MutableStateFlow<Boolean?>(null)

    override fun observeNoticePending(): Flow<Boolean> = noticePending
        .onStart {
            if (noticePending.value == null) {
                val stored = withContext(ioDispatcher) { fileSystem.exists(noticeFile) }
                // Progress set aside while the file was checked has already made it true.
                noticePending.compareAndSet(null, stored)
            }
        }
        .filterNotNull()

    // Finishes even if the caller is cancelled, such as when the scout taps OK and leaves.
    override suspend fun dismissNotice() = externalScope.runOutlivingCaller {
        withContext(ioDispatcher) {
            synchronized(this@FileDamagedProgressRepository) {
                // If it can't be deleted, the notice still goes for now, but shows again when the
                // app reopens.
                fileSystem.deleteOrWarn(noticeFile, warningLog, TAG)
                noticePending.value = false
            }
        }
    }

    /**
     * Moves [database] and the files SQLite keeps beside it into a new folder, named by when
     * they're set aside, and records that the scout needs telling. Copies set aside before are
     * kept, since an earlier one may hold more progress. [SetAsideDamagedDatabaseDriver] calls it
     * before Room opens the database, so a new one can be created in its place. Files that can't
     * be moved are deleted, as Android would delete them, so the new database doesn't open on top
     * of them. Recorded damage ([recordDamage]) is cleared once they're moved.
     */
    @Synchronized
    fun setAside(database: Path) {
        // Recorded damage outlives the files if the app was stopped after moving them all.
        if (fileSystem.exists(database)) {
            // Warned after the try, so writing the warning can't be mistaken for the move failing.
            val (message, error) = try {
                fileSystem.createDirectories(folder)
                // Before the move, so the scout is told, and the next launch sets aside the rest,
                // even if the app closes partway.
                createIfMissing(noticeFile)
                createIfMissing(damageFile)
                // Another name if this one is taken, since a copy that can't be made deletes the
                // database.
                val copy = generateSequence { folder / copyName() }.first { !fileSystem.exists(it) }
                fileSystem.createDirectory(copy, mustCreate = true)
                // The databases and no-backup folders are both in the app's data directory, so
                // each move is a rename on one file system, which atomicMove needs.
                for (file in sqliteFiles(database).filter { fileSystem.exists(it) }) {
                    fileSystem.atomicMove(file, copy / file.name)
                }
                "Set aside a damaged database: $database" to null
            } catch (e: IOException) {
                SQLiteDatabase.deleteDatabase(database.toFile())
                "Couldn't set aside a damaged database, so deleted it: $database" to e
            }
            noticePending.value = true
            warningLog.warn(TAG, message, error)
        }
        // If it can't be deleted, the new database Room makes is newer than it, so the next
        // launch leaves that alone (isDamageRecorded).
        fileSystem.deleteOrWarn(damageFile, warningLog, TAG)
    }

    /**
     * Records that SQLite found the database damaged while it was open, so it's set aside the
     * next time the app opens it ([SetAsideDamagedDatabaseDriver]). If that can't be recorded,
     * such as on a full disk, the next launch has only its check to find the damage. Damage the
     * check misses then crashes the app at each launch until there's room for the record, which
     * is an empty file, and the launch after that sets the database aside.
     *
     * The record is written even if it's there, so it's newer than the database
     * ([isDamageRecorded]) even when an older one couldn't be deleted.
     */
    @Synchronized
    fun recordDamage() {
        try {
            fileSystem.createDirectories(folder)
            fileSystem.write(damageFile) {}
        } catch (e: IOException) {
            warningLog.warn(TAG, "Couldn't record a damaged database", e)
        }
    }

    /**
     * Whether damage was recorded ([recordDamage]) and [database] not yet set aside. A record
     * older than [database] was left by a set-aside that couldn't delete it, such as when its
     * folder can't be written, since Room made [database] after it. Honoring it would set aside
     * the scout's new progress at every launch (#377), so it's deleted instead.
     *
     * Every statement fails once damage is found, but one already running on another connection
     * could still write the database after the record, so a real record looks older too. Then
     * the next launch has only its check, and damage the check misses crashes the app once more,
     * which records it again.
     */
    @Synchronized
    fun isDamageRecorded(database: Path): Boolean {
        val record = fileSystem.metadataOrNull(damageFile) ?: return false
        val recordedAt = record.lastModifiedAtMillis ?: return true
        val writtenAt = fileSystem.metadataOrNull(database)?.lastModifiedAtMillis ?: return true
        if (recordedAt >= writtenAt) return true
        fileSystem.deleteOrWarn(damageFile, warningLog, TAG)
        return false
    }

    /**
     * Creates [file] with nothing in it. One that's there is left alone, since opening it to
     * write could fail, which in [setAside] would delete the database rather than set it aside.
     */
    private fun createIfMissing(file: Path) {
        if (!fileSystem.exists(file)) fileSystem.write(file) {}
    }

    /**
     * When a copy is set aside, such as "2026-10-02T090607Z-", then a random suffix, so two set
     * aside in the same second get folders of their own.
     */
    private fun copyName() =
        Instant.fromEpochSeconds(clock.now().epochSeconds).toString().replace(":", "") + "-" +
            Random.nextULong()

    /**
     * The write-ahead log, shared-memory and rollback journal files SQLite keeps beside the
     * database, then the database file. The database goes last, so once it's gone, so are the
     * rest, and a new database never opens beside an old one's log.
     */
    private fun sqliteFiles(database: Path) =
        listOf("-wal", "-shm", "-journal", "").map { "$database$it".toPath() }

    companion object {
        /** The folder in the no-backup directory that holds the damaged databases. */
        const val FOLDER_NAME = "damaged-progress"

        private const val NOTICE_FILE_NAME = "notice-pending"
        private const val DAMAGE_FILE_NAME = "damage-found"
        private const val TAG = "DamagedProgress"
    }
}
