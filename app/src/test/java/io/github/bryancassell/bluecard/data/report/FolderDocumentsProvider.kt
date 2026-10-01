package io.github.bryancassell.bluecard.data.report

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

/**
 * A documents provider over [folder], like the ones the system file picker saves to, so tests
 * write and delete documents as the app does on a phone. Each document's ID is its file name.
 */
class FolderDocumentsProvider : DocumentsProvider() {
    lateinit var folder: File

    /** When true, opening a document throws, as a provider that refuses the app does. */
    var refuseOpening = false

    /** Modes it doesn't support, which it refuses as Google Drive refused "wt". */
    var unsupportedModes = emptySet<String>()

    /**
     * When true, writing to a document fails once it's open, as when a cloud drive loses its
     * connection.
     */
    var failWrites = false

    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: arrayOf(Root.COLUMN_ROOT_ID))

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val columns = projection ?: arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_SIZE)
        val file = File(folder, documentId)
        return MatrixCursor(columns).apply {
            addRow(
                columns.map {
                    when (it) {
                        Document.COLUMN_DOCUMENT_ID -> documentId
                        Document.COLUMN_SIZE -> file.length()
                        else -> null
                    }
                }
            )
        }
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor = MatrixCursor(projection ?: arrayOf(Document.COLUMN_DOCUMENT_ID))

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        if (refuseOpening) throw SecurityException("This app can't write here")
        if (mode in unsupportedModes) throw FileNotFoundException("Unsupported mode: $mode")
        val file = File(folder, documentId)
        val opened = ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
        if (!failWrites) return opened
        // Opened as asked, which can empty it, but only for reading, so writing to it fails.
        opened.close()
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun deleteDocument(documentId: String) {
        File(folder, documentId).delete()
    }
}
