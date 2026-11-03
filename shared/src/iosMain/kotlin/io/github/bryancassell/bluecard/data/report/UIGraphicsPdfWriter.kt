@file:OptIn(ExperimentalForeignApi::class)

package io.github.bryancassell.bluecard.data.report

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import okio.BufferedSink
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIGraphicsPDFRenderer
import platform.UIKit.UIGraphicsPDFRendererFormat

/**
 * Writes a report's pages with UIKit's [UIGraphicsPDFRenderer], drawing the text Core Text laid
 * out ([CoreTextText]). Reports are written off the main thread, which the renderer allows: its
 * Swift interface declares it `Sendable`. It needs the iOS simulator or a device, so it's checked
 * by an iOS test (UIGraphicsPdfWriterTest).
 */
class UIGraphicsPdfWriter : ReportPdfWriter<CoreTextText> {
    override fun write(pages: List<ReportPage<CoreTextText>>, out: BufferedSink) {
        val bounds = CGRectMake(0.0, 0.0, PAGE_WIDTH.toDouble(), PAGE_HEIGHT.toDouble())
        val renderer = UIGraphicsPDFRenderer(bounds, UIGraphicsPDFRendererFormat.defaultFormat())
        // An exception can't pass through UIKit's call of the block, which would end the app, so
        // one is kept and thrown once the renderer returns.
        var failure: Throwable? = null
        val pdf = renderer.PDFDataWithActions { context ->
            try {
                pages.forEach { page ->
                    context!!.beginPage()
                    page.texts.forEach { it.text.draw(context.CGContext, it.x, it.y) }
                }
            } catch (e: Throwable) {
                failure = e
            }
        }
        failure?.let { throw it }
        out.write(pdf.bytes!!.reinterpret<ByteVar>().readBytes(pdf.length.toInt()))
    }
}
