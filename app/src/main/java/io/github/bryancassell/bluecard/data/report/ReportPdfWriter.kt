package io.github.bryancassell.bluecard.data.report

import java.io.OutputStream

/**
 * Writes a report's pages as a PDF. Android's PdfDocument, which [PdfDocumentWriter] uses, only
 * runs on a device, so local tests replace it.
 */
interface ReportPdfWriter {
    /** Writes [pages] to [out] as a PDF. Throws an `IOException` if they can't be written. */
    fun write(pages: List<ReportPage>, out: OutputStream)
}
