package io.github.bryancassell.bluecard.data.report

import android.graphics.pdf.PdfDocument
import okio.BufferedSink

/**
 * Writes a report's pages with Android's [PdfDocument]. PdfDocument needs a device: Robolectric
 * doesn't have its native code, so it throws "document is closed!" (checked with Robolectric
 * 4.17). So this is checked by an instrumented test (androidTest/.../PdfDocumentWriterTest) and
 * left out of the local tests' coverage check.
 */
class PdfDocumentWriter : ReportPdfWriter {
    override fun write(pages: List<ReportPage>, out: BufferedSink) {
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, page ->
                val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
                val pdfPage = document.startPage(info)
                // Finished even if drawing fails, as close() throws on an unfinished page and
                // would hide the failure.
                try {
                    page.draw(pdfPage.canvas)
                } finally {
                    document.finishPage(pdfPage)
                }
            }
            document.writeTo(out.outputStream())
        } finally {
            document.close()
        }
    }
}
