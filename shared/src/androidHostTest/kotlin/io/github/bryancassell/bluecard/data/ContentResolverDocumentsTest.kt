package io.github.bryancassell.bluecard.data

import android.content.Context
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.testing.FakeWarningLog
import io.github.bryancassell.bluecard.testing.FolderDocumentsProvider
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Checks that saving is main-safe, as `Documents.save` promises: the content, and every call to
 * the document's provider, are on the IO dispatcher. What the save writes, and what it leaves
 * when it fails, are checked through the repositories that save (JsonBackupRepositoryTest,
 * PdfReportRepositoryTest).
 */
@RunWith(AndroidJUnit4::class)
class ContentResolverDocumentsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val authority = "io.github.bryancassell.bluecard.test.documents"

    // Like the provider the system file picker saves to.
    private val documentsProvider = FolderDocumentsProvider.create(authority)

    // A dispatcher whose thread the test can recognize, in the IO dispatcher's place.
    private val ioExecutor = Executors.newSingleThreadExecutor { Thread(it, IO_THREAD) }

    private val documents =
        ContentResolverDocuments(context, FakeWarningLog(), ioExecutor.asCoroutineDispatcher())

    @Before
    fun setUpDocuments() {
        documentsProvider.folder = folder.newFolder("documents")
    }

    @After
    fun stopIoExecutor() {
        ioExecutor.shutdown()
    }

    /** An empty document, as the file picker makes one to save to. */
    private fun createdDocument() =
        File(documentsProvider.folder, "BlueCard export.json").apply { createNewFile() }

    private fun File.uri() = DocumentsContract.buildDocumentUri(authority, name)

    @Test
    fun save_makesTheContentAndOpensTheDocumentOnTheIoDispatcher() = runTest {
        val file = createdDocument()
        var madeOn: String? = null

        documents.save(file.uri()) {
            madeOn = Thread.currentThread().name
            "Saved".encodeToByteArray()
        }

        assertEquals("Saved", file.readText())
        assertEquals(IO_THREAD, madeOn)
        assertTrue(documentsProvider.callThreads.isNotEmpty())
        assertEquals(listOf(IO_THREAD), documentsProvider.callThreads.distinct())
    }

    @Test
    fun save_withNoContent_deletesTheEmptyDocumentOnTheIoDispatcher() = runTest {
        val file = createdDocument()

        documents.save(file.uri()) { null }

        assertFalse(file.exists())
        assertTrue(documentsProvider.callThreads.isNotEmpty())
        assertEquals(listOf(IO_THREAD), documentsProvider.callThreads.distinct())
    }

    private companion object {
        const val IO_THREAD = "Test IO"
    }
}
