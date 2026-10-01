package io.github.bryancassell.bluecard.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private const val TAG = "Documents"

/**
 * Writes [content] to [destination], a document the scout chose with the system file picker,
 * replacing what it held. The content is made before the destination is opened, which empties
 * it, so content that can't be made leaves a file the scout chose to replace as it was. So
 * does no content (null), such as a report on a badge that was just cleared, which writes
 * nothing.
 *
 * Throws what making the content throws, or an [IOException] if the document can't be opened
 * or written. Either way, it doesn't leave behind the empty document the file picker made, or
 * one that opening emptied.
 */
suspend fun ContentResolver.saveDocument(destination: Uri, content: suspend () -> ByteArray?) {
    val bytes: ByteArray
    val out: OutputStream
    try {
        bytes = content() ?: run {
            // The file picker made an empty document for the content, which isn't left behind.
            deleteIfEmpty(destination)
            return
        }
        out = openForWriting(destination)
    } catch (e: Exception) {
        // The file picker made an empty document for the content, which isn't left behind.
        deleteIfEmpty(destination)
        throw e
    }
    try {
        out.use { it.write(bytes) }
    } catch (e: Exception) {
        // Opening it emptied it, so it's empty or partial, and the scout could send it
        // without noticing that it failed.
        delete(destination)
        throw e
    }
}

/**
 * Opens [source], such as a document the scout chose with the system file picker, to read. As
 * when one is opened to write ([openForWriting]), the app that holds it can refuse with an
 * exception that isn't an IOException, which is reported as one.
 */
fun ContentResolver.openDocument(source: Uri): InputStream = try {
    openInputStream(source)
} catch (e: RuntimeException) {
    throw IOException("Couldn't open $source", e)
} ?: throw IOException("Couldn't open $source: its provider recently crashed")

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
private fun ContentResolver.deleteIfEmpty(destination: Uri) {
    val size = try {
        // The form of query that providers take since Android O: DocumentsProvider refuses
        // the older one.
        val columns = arrayOf(OpenableColumns.SIZE)
        query(destination, columns, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }
    } catch (e: Exception) {
        // As in delete.
        Log.w(TAG, "Couldn't find the size of the document that failed", e)
        null
    }
    if (size == 0L) delete(destination)
}

/**
 * Deletes [destination], if its provider lets it. If it doesn't, the save's own failure is
 * what the scout is told about, so this one is only logged.
 */
private fun ContentResolver.delete(destination: Uri) {
    try {
        DocumentsContract.deleteDocument(this, destination)
    } catch (e: Exception) {
        // deleteDocument rethrows whatever the provider throws, such as an
        // UnsupportedOperationException when it can't delete.
        Log.w(TAG, "Couldn't delete the document that failed", e)
    }
}
