package io.github.bryancassell.bluecard.data.report

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.withTranslation
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import kotlin.math.abs
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Lays text out with StaticLayout and draws it into a bitmap, with Robolectric's native graphics,
 * which measure, break and draw text with real fonts, as a phone does. CoreTextTypesetterTest
 * checks iOS's typesetter the same way.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StaticLayoutTypesetterTest {
    private val typesetter = StaticLayoutTypesetter(Locale.US)
    private val words = (1..60).joinToString(" ") { "word$it" }

    @Test
    fun shortText_isOneLine() {
        val text = typesetter.layOut("Camping", ReportStyle.Body, 300)

        assertEquals(1, text.lineCount)
        assertEquals(0, text.lineStart(0))
        assertEquals(listOf("Camping"), text.lines())
    }

    @Test
    fun emptyText_isOneEmptyLine_asTallAsALine() {
        val text = typesetter.layOut("", ReportStyle.Body, 300)

        assertEquals(1, text.lineCount)
        assertEquals(typesetter.layOut("Camping", ReportStyle.Body, 300).height, text.height)
    }

    @Test
    fun longText_breaksIntoLines_withEveryWordOnce_inOrder() {
        val text = typesetter.layOut(words, ReportStyle.Body, 200)

        assertTrue("Expected several lines, got ${text.lineCount}", text.lineCount > 1)
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
                assertTrue("$style line $line is $height high", height >= style.size)
                top = text.lineBottom(line)
            }
        }
    }

    @Test
    fun formats_andDirection_areTheLocales() {
        val persian = StaticLayoutTypesetter(Locale.forLanguageTag("fa-IR"))

        assertFalse(typesetter.rightToLeft)
        assertTrue(persian.rightToLeft)
        assertEquals("Sep 30, 2026", typesetter.formats.formatDate(LocalDate(2026, 9, 30)))
    }

    @Test
    fun text_isDrawnAtItsPlace_withinItsWidthAndHeight() {
        val text = typesetter.layOut(words, ReportStyle.Body, 200)

        val ink = ink(text, x = 50f, y = 60f)

        assertTrue("Nothing was drawn", ink.found)
        assertTrue("Ink starts at x ${ink.left}", ink.left >= 50)
        assertTrue("Ink ends at x ${ink.right}", ink.right < 50 + 200)
        assertTrue("Ink starts at y ${ink.top}", ink.top >= 60)
        assertTrue("Ink ends at y ${ink.bottom}", ink.bottom < 60 + text.height)
    }

    @Test
    fun leftToRight_isDrawnFromTheLeft() {
        val ink = ink(typesetter.layOut("Camping", ReportStyle.Body, 200), x = 50f, y = 60f)

        assertTrue("Nothing was drawn", ink.found)
        assertTrue("Ink is at x ${ink.left}–${ink.right}", ink.left - 50 < 50 + 200 - ink.right)
    }

    @Test
    fun rightToLeft_isDrawnFromTheRight() {
        val rightToLeft = StaticLayoutTypesetter(Locale.forLanguageTag("fa-IR"))

        val ink = ink(rightToLeft.layOut("Camping", ReportStyle.Body, 200), x = 50f, y = 60f)

        assertTrue("Nothing was drawn", ink.found)
        assertTrue("Ink is at x ${ink.left}–${ink.right}", ink.left - 50 > 50 + 200 - ink.right)
        assertTrue("Ink ends at x ${ink.right}", ink.right < 50 + 200)
    }

    @Test
    fun centered_isDrawnInTheMiddle() {
        val text = typesetter.layOut("Page 1 of 2", ReportStyle.Footer, 200, centered = true)

        val ink = ink(text, x = 50f, y = 60f)

        assertTrue("Nothing was drawn", ink.found)
        // Pixels round, and the first and last letters' ink doesn't fill their whole width, so
        // the space on each side can differ by a point or two.
        val left = ink.left - 50
        val right = 50 + 200 - 1 - ink.right
        assertTrue("Space on the left: $left, on the right: $right", abs(left - right) <= 2)
    }

    @Test
    fun boldStyle_drawsMoreInk_thanBody() {
        val bold = ink(typesetter.layOut("Camping", ReportStyle.Subheading, 300), 50f, 60f)
        val body = ink(typesetter.layOut("Camping", ReportStyle.Body, 300), 50f, 60f)

        assertTrue("Bold ${bold.amount}, body ${body.amount}", bold.amount > body.amount)
    }

    /** Where drawing darkened a white page, in pixels from its top left corner. */
    private class Ink(
        val left: Int,
        val right: Int,
        val top: Int,
        val bottom: Int,
        val amount: Int
    ) {
        val found: Boolean get() = right >= left
    }

    /** Draws [text] at ([x], [y]) on a white, page-sized bitmap, and finds where it's dark. */
    private fun ink(text: StaticLayoutText, x: Float, y: Float): Ink {
        val bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.withTranslation(x, y) { text.draw(this) }
        val pixels = IntArray(PAGE_WIDTH * PAGE_HEIGHT)
        bitmap.getPixels(pixels, 0, PAGE_WIDTH, 0, 0, PAGE_WIDTH, PAGE_HEIGHT)
        var left = PAGE_WIDTH
        var right = -1
        var top = PAGE_HEIGHT
        var bottom = -1
        var amount = 0
        for (row in 0 until PAGE_HEIGHT) {
            for (column in 0 until PAGE_WIDTH) {
                val darkness = 255 - Color.red(pixels[row * PAGE_WIDTH + column])
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
    }
}
