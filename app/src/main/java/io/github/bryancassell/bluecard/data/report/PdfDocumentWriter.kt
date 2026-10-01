package io.github.bryancassell.bluecard.data.report

import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import javax.inject.Inject

/**
 * Writes a report's pages with Android's [PdfDocument]. PdfDocument needs a device, so this
 * is checked by an instrumented test (androidTest/.../PdfDocumentWriterTest) and left out of
 * the local tests' coverage check.
 */
class PdfDocumentWriter @Inject constructor() : ReportPdfWriter {
    override fun write(pages: List<ReportPage>, out: OutputStream) {
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, page ->
                val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
                val pdfPage = document.startPage(info)
                page.draw(pdfPage.canvas)
                document.finishPage(pdfPage)
            }
            document.writeTo(out)
        } finally {
            document.close()
        }
    }
}
