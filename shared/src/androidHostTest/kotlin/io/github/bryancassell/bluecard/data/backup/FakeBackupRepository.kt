package io.github.bryancassell.bluecard.data.backup

import android.net.Uri
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred

/**
 * A [BackupRepository] for other features' tests. It records exports, imports and merges
 * instead of making them, and reads files from [files].
 */
class FakeBackupRepository : BackupRepository {
    /**
     * When true, [exportBackup], [importBackup] and [mergeBackup] throw, as the real
     * repository's do when the file or the scout's data can't be saved.
     */
    var failSaves = false

    /** When true, [readBackup] throws, as the real repository's does when it can't read a file. */
    var failReads = false

    /** When set, each function waits for it first, as if it took that long. */
    var working: CompletableDeferred<Unit>? = null

    /** What [readBackup] finds in each file. Any other file isn't an export. */
    val files = mutableMapOf<Uri, BackupReadResult>()

    /** Where each export was saved to, in order. */
    val exported = mutableListOf<Uri>()

    /** Each backup imported, in order. */
    val imported = mutableListOf<Backup>()

    /** Each merge, in order. */
    val merged = mutableListOf<Merge>()

    override suspend fun exportBackup(destination: Uri) {
        working?.await()
        if (failSaves) throw IOException("Save failed")
        exported += destination
    }

    override suspend fun readBackup(source: Uri): BackupReadResult {
        working?.await()
        if (failReads) throw IOException("Read failed")
        return files[source] ?: BackupReadResult.Invalid
    }

    override suspend fun importBackup(backup: Backup) {
        working?.await()
        if (failSaves) throw IOException("Save failed")
        imported += backup
    }

    override suspend fun mergeBackup(
        backup: Backup,
        fromFile: Set<String>,
        profileFromFile: Boolean
    ) {
        working?.await()
        if (failSaves) throw IOException("Save failed")
        merged += Merge(backup, fromFile, profileFromFile)
    }

    /** A call to [mergeBackup]. */
    data class Merge(val backup: Backup, val fromFile: Set<String>, val profileFromFile: Boolean)
}
