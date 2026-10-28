package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.Lock
import io.github.bryancassell.bluecard.data.deleteOrWarn
import io.github.bryancassell.bluecard.data.runOutlivingCaller
import io.github.bryancassell.bluecard.data.systemFileSystem
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
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath

/**
 * [DamagedProgressRepository] that keeps each damaged database in a folder of its own, in the
 * directory [produceDirectory] gives, with a file there while the scout hasn't been told.
 *
 * A singleton, so the notice shows as soon as [setAside] runs, without the app reopening. The
 * notice file and [noticePending] change together, under [lock].
 *
 * @param produceDirectory the directory for [FOLDER_NAME], called when it's first needed, off
 *   the main thread, since finding it can create it. Backups must leave it out, so a phone
 *   restored from one gets neither the damaged copies nor a notice about progress it never had,
 *   as Android's no-backup directory is left out. It must be on the database's file system, so
 *   each file moves by renaming it ([setAside]).
 */
class FileDamagedProgressRepository(
    produceDirectory: () -> Path,
    private val clock: ZonedClock,
    private val warningLog: WarningLog,
    private val externalScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher
) : DamagedProgressRepository {
    private val fileSystem = systemFileSystem
    private val lock = Lock()

    private val folder by lazy { produceDirectory() / FOLDER_NAME }
    private val noticeFile by lazy { folder / NOTICE_FILE_NAME }
    private val damageFile by lazy { folder / DAMAGE_FILE_NAME }

    // Null until noticeFile is first checked.
    private val noticePending = MutableStateFlow<Boolean?>(null)

    override fun observeNoticePending(): Flow<Boolean> = noticePending
        .onStart {
            if (noticePending.value == null) {
                // Under lock, so it can't see a notice file that setAside makes and then deletes.
                val stored = withContext(ioDispatcher) {
                    lock.withLock { fileSystem.exists(noticeFile) }
                }
                // Progress set aside while the file was checked has already made it true.
                noticePending.compareAndSet(null, stored)
            }
        }
        .filterNotNull()

    // Finishes even if the caller is cancelled, such as when the scout taps OK and leaves.
    override suspend fun dismissNotice() = externalScope.runOutlivingCaller {
        withContext(ioDispatcher) {
            lock.withLock {
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
     * of them. Recorded damage ([recordDamage]) is cleared once they're moved or deleted.
     *
     * Returns false if they can be neither moved nor deleted. BlueCard doesn't start over then,
     * so the scout isn't told it did, and recorded damage isn't cleared.
     */
    fun setAside(database: Path): Boolean = lock.withLock {
        // Recorded damage outlives the files if the app was stopped after moving them all.
        if (fileSystem.exists(database)) {
            var noticeCreated = false
            var copy: Path? = null
            // Warned after the try, so writing the warning can't be mistaken for the move failing.
            val (message, error) = try {
                fileSystem.createDirectories(folder)
                // Before the move, so the scout is told, and the next launch sets aside the rest,
                // even if the app closes partway. Found before writing, which can create the
                // file and then fail.
                noticeCreated = !fileSystem.exists(noticeFile)
                createIfMissing(noticeFile)
                createIfMissing(damageFile)
                // Another name if this one is taken, since a copy that can't be made deletes the
                // database.
                copy = generateSequence { folder / copyName() }.first { !fileSystem.exists(it) }
                fileSystem.createDirectory(copy, mustCreate = true)
                // The folder is on the database's file system (produceDirectory), so each move
                // is a rename, which atomicMove needs.
                for (file in sqliteFiles(database).filter { fileSystem.exists(it) }) {
                    fileSystem.atomicMove(file, copy / file.name)
                }
                "Set aside a damaged database: $database" to null
            } catch (e: IOException) {
                // A folder nothing was moved into isn't a copy, and each launch that fails again
                // would leave another.
                copy?.takeIf { fileSystem.listOrNull(it)?.isEmpty() == true }
                    ?.let { fileSystem.deleteOrWarn(it, warningLog, TAG) }
                try {
                    delete(database)
                    "Couldn't set aside a damaged database, so deleted it: $database" to e
                } catch (deleteError: IOException) {
                    e.addSuppressed(deleteError)
                    // A notice that was already pending is still owed to the scout.
                    if (noticeCreated) fileSystem.deleteOrWarn(noticeFile, warningLog, TAG)
                    val kept = "Couldn't set aside or delete a damaged database: $database"
                    warningLog.warn(TAG, kept, e)
                    return@withLock false
                }
            }
            noticePending.value = true
            warningLog.warn(TAG, message, error)
        }
        // If it can't be deleted, the new database Room makes is newer than it, so the next
        // launch leaves that alone (isDamageRecorded).
        fileSystem.deleteOrWarn(damageFile, warningLog, TAG)
        true
    }

    /**
     * Records that the database was found damaged while it was open, so it's set aside the
     * next time the app opens it ([SetAsideDamagedDatabaseDriver]), unless the database is
     * written after the record ([isDamageRecorded]). If that can't be recorded, such as on a full
     * disk, the next launch has only its check to find the damage. Damage the check misses then
     * crashes the app at each launch until there's room for the record, which is an empty file,
     * and the launch after that sets the database aside.
     *
     * The record is written even if it's there, so it's newer than the database
     * ([isDamageRecorded]) even when an older one couldn't be deleted.
     */
    fun recordDamage() = lock.withLock {
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
     * the scout's new progress at every launch (#377), so it doesn't count, and
     * [clearLeftoverDamageRecord] deletes it. A record as old as [database] counts, since
     * damage can be found in the same tick of the file clock as the database's last write.
     *
     * Every statement fails once damage is found, but SQLite can still write the database file
     * after the record: a checkpoint after a commit already running on another connection, or
     * when the last connection closes, which nothing in the app does. A real record then looks
     * older too, so the next launch has only its check, and damage the check misses crashes the
     * app once more, which records it again. File times come from the phone's clock, so a clock
     * set back by more than the time between the two writes can make a real record look older,
     * or one left over look newer, until the clock catches up.
     */
    fun isDamageRecorded(database: Path): Boolean {
        val record = fileSystem.metadataOrNull(damageFile) ?: return false
        val recordedAt = record.lastModifiedAtMillis ?: return true
        val writtenAt = fileSystem.metadataOrNull(database)?.lastModifiedAtMillis ?: return true
        return recordedAt >= writtenAt
    }

    /**
     * Deletes recorded damage that's older than [database], which [isDamageRecorded] doesn't
     * count. If it can't be deleted, it stays older, so it still doesn't count.
     */
    fun clearLeftoverDamageRecord(database: Path) = lock.withLock {
        if (fileSystem.exists(damageFile) && !isDamageRecorded(database)) {
            fileSystem.deleteOrWarn(damageFile, warningLog, TAG)
        }
    }

    /**
     * Creates [file] with nothing in it. One that's there is left alone, since opening it to
     * write could fail, which in [setAside] would delete the database rather than set it aside.
     */
    private fun createIfMissing(file: Path) {
        if (!fileSystem.exists(file)) fileSystem.write(file) {}
    }

    /**
     * Deletes [database] and the files SQLite keeps beside it, in [sqliteFiles]' order, stopping
     * at the first that can't be deleted, so the database goes only once the rest have. SQLite
     * keeps no other files beside the app's database. Master journals, which Android's
     * SQLiteDatabase.deleteDatabase also deletes, are only written for a transaction across
     * attached databases, and the app attaches none.
     */
    private fun delete(database: Path) {
        for (file in sqliteFiles(database)) fileSystem.delete(file)
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
        /** The folder that holds the damaged databases, in the directory produceDirectory gives. */
        const val FOLDER_NAME = "damaged-progress"

        private const val NOTICE_FILE_NAME = "notice-pending"
        private const val DAMAGE_FILE_NAME = "damage-found"
        private const val TAG = "DamagedProgress"
    }
}
