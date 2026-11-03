package io.github.bryancassell.bluecard.di

import io.github.bryancassell.bluecard.data.DocumentUri
import io.github.bryancassell.bluecard.data.Documents
import io.github.bryancassell.bluecard.data.FileUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import okio.Source
import platform.Foundation.NSURL

class UnfinishedReportsTest {
    private val destination = FileUrl(NSURL.fileURLWithPath("/tmp/Camping merit badge report.pdf"))

    /** Documents that keep what each save made, as [Documents.save] deletes an empty one for none. */
    private class SavedContent : Documents {
        val saved = mutableListOf<Pair<DocumentUri, ByteArray?>>()

        override suspend fun save(destination: DocumentUri, content: suspend () -> ByteArray?) {
            saved += destination to content()
        }

        override fun open(source: DocumentUri): Source = error("Not read")
    }

    @Test
    fun createReportToShare_makesNoReport() = runTest {
        assertNull(UnfinishedReports(SavedContent()).createReportToShare("camping"))
    }

    @Test
    fun saveReport_savesNoContent_soNoEmptyFileIsLeft() = runTest {
        val documents = SavedContent()

        UnfinishedReports(documents).saveReport("camping", destination)

        assertEquals(listOf<Pair<DocumentUri, ByteArray?>>(destination to null), documents.saved)
    }
}
