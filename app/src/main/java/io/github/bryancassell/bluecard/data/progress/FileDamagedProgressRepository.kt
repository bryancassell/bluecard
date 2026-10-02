package io.github.bryancassell.bluecard.data.progress

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.bryancassell.bluecard.data.runOutlivingCaller
import io.github.bryancassell.bluecard.di.ApplicationScope
import io.github.bryancassell.bluecard.di.IoDispatcher
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Clock
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/**
 * [DamagedProgressRepository] that keeps each damaged database in a folder of its own, in the
 * app's no-backup directory, with a file there while the scout hasn't been told. Auto Backup
 * skips that directory, so a phone restored from a backup gets neither the damaged copies nor
 * a notice about progress it never had.
 *
 * A singleton, so the notice shows as soon as [setAside] runs, without the app reopening. The
 * notice file and [noticePending] change together, under this object's lock.
 */
@Singleton
class FileDamagedProgressRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
    @param:ApplicationScope private val externalScope: CoroutineScope,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : DamagedProgressRepository {
    // Found when first used, off the main thread: finding the no-backup directory creates it.
    private val folder by lazy { File(context.noBackupFilesDir, FOLDER_NAME) }
    private val noticeFile by lazy { File(folder, NOTICE_FILE_NAME) }

    // Null until noticeFile is first checked.
    private val noticePending = MutableStateFlow<Boolean?>(null)

    override fun observeNoticePending(): Flow<Boolean> = noticePending
        .onStart {
            if (noticePending.value == null) {
                val stored = withContext(ioDispatcher) { noticeFile.exists() }
                // Progress set aside while the file was checked has already made it true.
                noticePending.compareAndSet(null, stored)
            }
        }
        .filterNotNull()

    // Finishes even if the caller is cancelled, such as when the scout taps OK and leaves.
    override suspend fun dismissNotice() = externalScope.runOutlivingCaller {
        withContext(ioDispatcher) {
            synchronized(this@FileDamagedProgressRepository) {
                noticeFile.delete()
                noticePending.value = false
            }
        }
    }

    /**
     * Moves [database] and the files SQLite keeps beside it into a new folder, named by when
     * they're set aside, and records that the scout needs telling. Copies set aside before are
     * kept, since an earlier one may hold more progress. SQLite's corruption handler calls it
     * ([SetAsideDamagedDatabaseFactory]) once the database is closed, so a new one can be
     * created in its place. Files that can't be moved are deleted, as Android would delete
     * them, so the new database doesn't open on top of them.
     */
    @Synchronized
    fun setAside(database: File) {
        // Two connections can each find the damage; the second finds the files already moved.
        if (!database.exists()) return
        try {
            Files.createDirectories(folder.toPath())
            // Before the move, so the scout is told even if the app closes partway.
            noticeFile.createNewFile()
            val copy = Files.createTempDirectory(folder.toPath(), copyNamePrefix())
            for (file in sqliteFiles(database).filter { it.exists() }) {
                Files.move(file.toPath(), copy.resolve(file.name))
            }
            Log.w(TAG, "Set aside a damaged database: $database")
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't set aside a damaged database, so deleted it: $database", e)
            SQLiteDatabase.deleteDatabase(database)
        }
        noticePending.value = true
    }

    /** When a copy is set aside, such as "2026-10-02T090607Z-", then a unique suffix. */
    private fun copyNamePrefix() =
        clock.instant().truncatedTo(ChronoUnit.SECONDS).toString().replace(":", "") + "-"

    /**
     * The write-ahead log, shared-memory and rollback journal files SQLite keeps beside the
     * database, then the database file. The database goes last, so if the app is stopped
     * partway, SQLite finds the damage again at the next open, and the rest is set aside too.
     */
    private fun sqliteFiles(database: File) =
        listOf("-wal", "-shm", "-journal", "").map { File(database.path + it) }

    companion object {
        /** The folder in the no-backup directory that holds the damaged databases. */
        const val FOLDER_NAME = "damaged-progress"

        private const val NOTICE_FILE_NAME = "notice-pending"
        private const val TAG = "DamagedProgress"
    }
}
