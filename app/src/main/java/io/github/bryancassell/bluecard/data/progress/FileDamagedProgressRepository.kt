package io.github.bryancassell.bluecard.data.progress

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.bryancassell.bluecard.di.IoDispatcher
import java.io.File
import java.io.IOException
import java.nio.file.Files
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/**
 * [DamagedProgressRepository] that keeps the damaged database in a folder of the app's
 * no-backup directory, with a file there while the scout hasn't been told. Auto Backup skips
 * that directory, so a phone restored from a backup gets neither the damaged copy nor a notice
 * about progress it never had.
 *
 * A singleton, so the notice shows as soon as [setAside] runs, without the app reopening.
 */
@Singleton
class FileDamagedProgressRepository @Inject constructor(
    @ApplicationContext context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : DamagedProgressRepository {
    private val folder = File(context.noBackupFilesDir, FOLDER_NAME)
    private val noticeFile = File(folder, NOTICE_FILE_NAME)

    // Null until noticeFile is first checked.
    private val noticePending = MutableStateFlow<Boolean?>(null)

    override fun observeNoticePending(): Flow<Boolean> = noticePending
        .onStart {
            val stored = withContext(ioDispatcher) { noticeFile.exists() }
            // Progress set aside while the file was checked has already made it true.
            noticePending.compareAndSet(null, stored)
        }
        .filterNotNull()

    override suspend fun dismissNotice() {
        withContext(ioDispatcher) { noticeFile.delete() }
        noticePending.value = false
    }

    /**
     * Moves [database] and the files SQLite keeps beside it into the folder, replacing any set
     * aside before, and records that the scout needs telling. SQLite's corruption handler calls
     * it ([SetAsideDamagedDatabaseFactory]) once the database is closed, so a new one can be
     * created in its place. Files that can't be moved are deleted, as Android would delete
     * them, so the new database doesn't open on top of them.
     */
    @Synchronized
    fun setAside(database: File) {
        // Two connections can each find the damage; the second finds the files already moved.
        if (!database.exists()) return
        try {
            folder.deleteRecursively()
            Files.createDirectories(folder.toPath())
            // Before the move, so the scout is told even if the app closes partway.
            Files.createFile(noticeFile.toPath())
            for (file in sqliteFiles(database).filter { it.exists() }) {
                Files.move(file.toPath(), File(folder, file.name).toPath())
            }
            Log.w(TAG, "Set aside a damaged database: $database")
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't set aside a damaged database, so deleted it: $database", e)
            SQLiteDatabase.deleteDatabase(database)
        }
        noticePending.value = true
    }

    /**
     * The database file, and the rollback journal, write-ahead log and shared-memory files
     * SQLite keeps beside it.
     */
    private fun sqliteFiles(database: File) =
        listOf("", "-journal", "-wal", "-shm").map { File(database.path + it) }

    companion object {
        /** The folder in the no-backup directory that holds the damaged database. */
        const val FOLDER_NAME = "damaged-progress"

        private const val NOTICE_FILE_NAME = "notice-pending"
        private const val TAG = "DamagedProgress"
    }
}
