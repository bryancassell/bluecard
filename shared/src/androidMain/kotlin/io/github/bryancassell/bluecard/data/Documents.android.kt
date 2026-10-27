package io.github.bryancassell.bluecard.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import io.github.bryancassell.bluecard.di.IoDispatcher
import io.github.bryancassell.bluecard.di.WarningLog
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.Source
import okio.source

private const val TAG = "Documents"

actual typealias DocumentUri = Uri

/**
 * [Documents] through the content resolver, so they reach any document the scout picks. Saving
 * switches to [ioDispatcher], since it waits on the document's provider, such as a cloud drive.
 */
class ContentResolverDocuments(
    private val context: Context,
    private val warningLog: WarningLog,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : Documents {
    override suspend fun save(destination: DocumentUri, content: suspend () -> ByteArray?) =
        withContext(ioDispatcher) {
            context.contentResolver.saveDocument(destination, warningLog, content)
        }

    override fun open(source: DocumentUri): Source = context.contentResolver.openDocument(source)
}

/**
 * Writes [content] to [destination], a document the scout chose with the system file picker,
 * as [Documents.save] describes. If the document that failed can't be deleted, or its size
 * can't be read, that's written to [warningLog].
 */
private suspend fun ContentResolver.saveDocument(
    destination: Uri,
    warningLog: WarningLog,
    content: suspend () -> ByteArray?
) {
    val bytes: ByteArray
    val out: OutputStream
    try {
        bytes = content() ?: run {
            // The file picker made an empty document for the content, which isn't left behind.
            deleteIfEmpty(destination, warningLog)
            return
        }
        out = openForWriting(destination)
    } catch (e: Exception) {
        // The file picker made an empty document for the content, which isn't left behind.
        deleteIfEmpty(destination, warningLog)
        throw e
    }
    try {
        out.use { it.write(bytes) }
    } catch (e: Exception) {
        // Opening it emptied it, so it's empty or partial, and the scout could send it
        // without noticing that it failed.
        delete(destination, warningLog)
        throw e
    }
}

/**
 * Opens [source], such as a document the scout chose with the system file picker, to read. As
 * when one is opened to write ([openForWriting]), the app that holds it can refuse with an
 * exception that isn't an IOException, which is reported as one.
 */
private fun ContentResolver.openDocument(source: Uri): Source = try {
    openInputStream(source)
} catch (e: RuntimeException) {
    throw IOException("Couldn't open $source", e)
}?.source() ?: throw IOException("Couldn't open $source: its provider recently crashed")

/**
 * Opens [destination], truncating it: with "w", a provider may leave the end of a longer file
 * that was there before (ContentResolver.openOutputStream). A provider that doesn't support
 * "wt" is asked for "rwt": Google Drive refused "wt" with a FileNotFoundException, but
 * truncated with "rwt" (https://issuetracker.google.com/issues/180526528), and
 * DocumentsProvider.openDocument says to refuse a mode with an UnsupportedOperationException.
 *
 * The app that holds the destination, such as a cloud drive, can refuse with an exception
 * that isn't an IOException, such as a SecurityException. That's no mistake in BlueCard's
 * code, so it's reported as one.
 */
private fun ContentResolver.openForWriting(destination: Uri): OutputStream = try {
    try {
        openOutputStream(destination, "wt")
    } catch (e: FileNotFoundException) {
        openOutputStream(destination, "rwt")
    } catch (e: UnsupportedOperationException) {
        openOutputStream(destination, "rwt")
    }
} catch (e: RuntimeException) {
    throw IOException("Couldn't open $destination", e)
} ?: throw IOException("Couldn't open $destination: its provider recently crashed")

/**
 * Deletes [destination] if its provider says it's empty, as the file picker makes it. If it
 * doesn't say, it's kept, in case it's a file the scout chose to replace.
 */
private fun ContentResolver.deleteIfEmpty(destination: Uri, warningLog: WarningLog) {
    val size = try {
        // The form of query that providers take since Android O: DocumentsProvider refuses
        // the older one.
        val columns = arrayOf(OpenableColumns.SIZE)
        query(destination, columns, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }
    } catch (e: Exception) {
        // As in delete.
        warningLog.warn(TAG, "Couldn't find the size of the document that failed", e)
        null
    }
    if (size == 0L) delete(destination, warningLog)
}

/**
 * Deletes [destination], if its provider lets it. If it doesn't, the save's own failure is
 * what the scout is told about, so this one is only written to [warningLog].
 */
private fun ContentResolver.delete(destination: Uri, warningLog: WarningLog) {
    try {
        DocumentsContract.deleteDocument(this, destination)
    } catch (e: Exception) {
        // deleteDocument rethrows whatever the provider throws, such as an
        // UnsupportedOperationException when it can't delete.
        warningLog.warn(TAG, "Couldn't delete the document that failed", e)
    }
}
