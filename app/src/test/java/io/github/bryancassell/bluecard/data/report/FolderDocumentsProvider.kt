package io.github.bryancassell.bluecard.data.report

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File

/**
 * A documents provider over [folder], like the ones the system file picker saves to, so tests
 * write and delete documents as the app does on a phone. Each document's ID is its file name.
 */
class FolderDocumentsProvider : DocumentsProvider() {
    lateinit var folder: File

    /** When true, opening a document throws, as a provider that refuses the app does. */
    var refuseOpening = false

    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: arrayOf(Root.COLUMN_ROOT_ID))

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: arrayOf(Document.COLUMN_DOCUMENT_ID))

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
        val file = File(folder, documentId)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun deleteDocument(documentId: String) {
        File(folder, documentId).delete()
    }
}
