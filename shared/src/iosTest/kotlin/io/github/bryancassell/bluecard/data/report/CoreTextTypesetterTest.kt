@file:OptIn(ExperimentalForeignApi::class)

package io.github.bryancassell.bluecard.data.report

import io.github.bryancassell.bluecard.text.EnglishLocaleFormats
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextGetBytesPerRow
import platform.CoreGraphics.CGBitmapContextGetData
import platform.CoreGraphics.CGColorSpaceCreateDeviceGray
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextSetGrayFillColor
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGRectMake

/** Lays text out with Core Text and draws it into a bitmap, on the iOS simulator. */
class CoreTextTypesetterTest {
    private val typesetter = CoreTextTypesetter(EnglishLocaleFormats, rightToLeft = false)
    private val words = (1..60).joinToString(" ") { "word$it" }

    @Test
    fun shortText_isOneLine() {
        val text = typesetter.layOut("Camping", ReportStyle.Body, 300)

        assertEquals(1, text.lineCount)
        assertEquals(0, text.lineStart(0))
        assertEquals(listOf("Camping"), text.lines())
    }

    // Unlike StaticLayout, which gives empty text one empty line.
    @Test
    fun emptyText_hasNoLines_andNoHeight() {
        val text = typesetter.layOut("", ReportStyle.Body, 300)

        assertEquals(0, text.lineCount)
        assertEquals(0f, text.height)
    }

    @Test
    fun longText_breaksIntoLines_withEveryWordOnce_inOrder() {
        val text = typesetter.layOut(words, ReportStyle.Body, 200)

        assertTrue(text.lineCount > 1, "Expected several lines, got ${text.lineCount}")
        assertEquals(words.split(" "), text.lines().flatMap { it.split(" ") })
    }

    @Test
    fun lineBreak_startsANewLine() {
        val text = typesetter.layOut("Planned with my patrol.\nWe camped.", ReportStyle.Body, 300)

        assertEquals(listOf("Planned with my patrol.", "We camped."), text.lines())
    }

    // As ReportTypesetter asks, so a paragraph split across pages reads the same.
    @Test
    fun restOfAParagraph_laidOutOnItsOwn_breaksTheSameWay() {
        val whole = typesetter.layOut(words, ReportStyle.Body, 200)

        val rest = typesetter.layOut(words.substring(whole.lineStart(2)), ReportStyle.Body, 200)

        assertEquals(whole.lines().drop(2), rest.lines())
    }

    @Test
    fun lines_areEachBelowTheOneBefore_atLeastTheFontsSizeApart() {
        ReportStyle.entries.forEach { style ->
            val text = typesetter.layOut(words, style, 200)

            var top = 0f
            repeat(text.lineCount) { line ->
                val height = text.lineBottom(line) - top
                assertTrue(height >= style.size, "$style line $line is $height high")
                top = text.lineBottom(line)
            }
            assertEquals(text.lineBottom(text.lineCount - 1), text.height)
        }
    }

    @Test
    fun text_isDrawnAtItsPlace_withinItsWidthAndHeight() {
        val text = typesetter.layOut(words, ReportStyle.Body, 200)

        val ink = ink(text, x = 50f, y = 60f)

        assertTrue(ink.left >= 50, "Ink starts at x ${ink.left}")
        assertTrue(ink.right < 50 + 200, "Ink ends at x ${ink.right}")
        assertTrue(ink.top >= 60, "Ink starts at y ${ink.top}")
        assertTrue(ink.bottom < 60 + text.height, "Ink ends at y ${ink.bottom}")
    }

    @Test
    fun leftToRight_isDrawnFromTheLeft() {
        val ink = ink(typesetter.layOut("Camping", ReportStyle.Body, 200), x = 50f, y = 60f)

        assertTrue(ink.left - 50 < 50 + 200 - ink.right, "Ink is at x ${ink.left}–${ink.right}")
    }

    @Test
    fun rightToLeft_isDrawnFromTheRight() {
        val rightToLeft = CoreTextTypesetter(EnglishLocaleFormats, rightToLeft = true)

        val ink = ink(rightToLeft.layOut("Camping", ReportStyle.Body, 200), x = 50f, y = 60f)

        assertTrue(ink.left - 50 > 50 + 200 - ink.right, "Ink is at x ${ink.left}–${ink.right}")
        assertTrue(ink.right < 50 + 200, "Ink ends at x ${ink.right}")
    }

    @Test
    fun centered_isDrawnInTheMiddle() {
        val text = typesetter.layOut("Page 1 of 2", ReportStyle.Footer, 200, centered = true)

        val ink = ink(text, x = 50f, y = 60f)

        // Pixels round, and the first and last letters' ink doesn't fill their whole width, so
        // the space on each side can differ by a point or two.
        val left = ink.left - 50
        val right = 50 + 200 - 1 - ink.right
        assertTrue(abs(left - right) <= 2, "Space on the left: $left, on the right: $right")
    }

    @Test
    fun boldStyle_drawsMoreInk_thanBody() {
        val bold = ink(typesetter.layOut("Camping", ReportStyle.Subheading, 300), 10f, 10f)
        val body = ink(typesetter.layOut("Camping", ReportStyle.Body, 300), 10f, 10f)

        assertTrue(bold.amount > body.amount, "Bold ${bold.amount}, body ${body.amount}")
    }

    /** Where [text] drew on a white page, and how dark it made it altogether. */
    private class Ink(val left: Int, val right: Int, val top: Int, val bottom: Int, val amount: Int)

    /**
     * Draws [text] at ([x], [y]) on a white, page-sized bitmap, with UIKit's coordinates, as
     * UIGraphicsPDFRenderer's are, and finds where it's dark.
     */
    private fun ink(text: CoreTextText, x: Float, y: Float): Ink {
        val width = PAGE_WIDTH
        val height = PAGE_HEIGHT
        val gray = CGColorSpaceCreateDeviceGray()
        val context = CGBitmapContextCreate(
            null,
            width.toULong(),
            height.toULong(),
            8u,
            0u,
            gray,
            CGImageAlphaInfo.kCGImageAlphaNone.value
        )
        try {
            CGContextSetGrayFillColor(context, 1.0, 1.0)
            CGContextFillRect(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
            CGContextTranslateCTM(context, 0.0, height.toDouble())
            CGContextScaleCTM(context, 1.0, -1.0)
            text.draw(context, x, y)
            // The bitmap's first row is the page's top.
            val pixels = CGBitmapContextGetData(context)!!.reinterpret<UByteVar>()
            val rowBytes = CGBitmapContextGetBytesPerRow(context).toInt()
            var left = width
            var right = -1
            var top = height
            var bottom = -1
            var amount = 0
            for (row in 0 until height) {
                for (column in 0 until width) {
                    val darkness = 255 - pixels[row * rowBytes + column].toInt()
                    if (darkness > 128) {
                        left = minOf(left, column)
                        right = maxOf(right, column)
                        top = minOf(top, row)
                        bottom = maxOf(bottom, row)
                    }
                    amount += darkness
                }
            }
            return Ink(left, right, top, bottom, amount)
        } finally {
            CGContextRelease(context)
            CGColorSpaceRelease(gray)
        }
    }
}
