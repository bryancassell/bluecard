package io.github.bryancassell.bluecard.data

import android.Manifest
import android.content.Context
import android.content.pm.ProviderInfo
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/**
 * Checks that saving is main-safe, as `Documents.save` promises. What the save writes, and what
 * it leaves when it fails, are checked through the repositories that save
 * (JsonBackupRepositoryTest, PdfReportRepositoryTest).
 */
@RunWith(AndroidJUnit4::class)
class ContentResolverDocumentsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val authority = "io.github.bryancassell.bluecard.test.documents"

    // Like the provider the system file picker saves to.
    private val documentsProvider = Robolectric.buildContentProvider(
        FolderDocumentsProvider::class.java
    ).create(
        ProviderInfo().apply {
            authority = this@ContentResolverDocumentsTest.authority
            exported = true
            grantUriPermissions = true
            readPermission = Manifest.permission.MANAGE_DOCUMENTS
            writePermission = Manifest.permission.MANAGE_DOCUMENTS
        }
    ).get()

    // A dispatcher whose thread the test can recognize, in the IO dispatcher's place.
    private val ioExecutor = Executors.newSingleThreadExecutor { Thread(it, IO_THREAD) }

    @After
    fun stopIoExecutor() {
        ioExecutor.shutdown()
    }

    @Test
    fun save_makesAndWritesTheContentOnTheIoDispatcher() = runTest {
        documentsProvider.folder = folder.newFolder("documents")
        // Empty, as the file picker makes it.
        val file = File(documentsProvider.folder, "BlueCard export.json").apply { createNewFile() }
        val documents =
            ContentResolverDocuments(context, FakeWarningLog(), ioExecutor.asCoroutineDispatcher())
        var madeOn: String? = null

        documents.save(DocumentsContract.buildDocumentUri(authority, file.name)) {
            madeOn = Thread.currentThread().name
            "Saved".encodeToByteArray()
        }

        assertEquals(IO_THREAD, madeOn)
        assertEquals("Saved", file.readText())
    }

    private companion object {
        const val IO_THREAD = "Test IO"
    }
}
