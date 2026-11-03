package io.github.bryancassell.bluecard.data.report

import okio.BufferedSink

/**
 * Writes a report's pages as a PDF, drawing the text that the platform's [ReportTypesetter] laid
 * out. The platforms' PDF writers only run on a device or simulator, so local tests replace them.
 */
interface ReportPdfWriter<T : ReportText> {
    /** Writes [pages] to [out] as a PDF. Throws an `IOException` if they can't be written. */
    fun write(pages: List<ReportPage<T>>, out: BufferedSink)
}
