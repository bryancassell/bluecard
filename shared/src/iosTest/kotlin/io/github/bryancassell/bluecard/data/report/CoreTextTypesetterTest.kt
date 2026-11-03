@file:OptIn(ExperimentalForeignApi::class)

package io.github.bryancassell.bluecard.data.report

import io.github.bryancassell.bluecard.text.EnglishLocaleFormats
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGAffineTransform
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGContextGetTextMatrix
import platform.CoreGraphics.CGContextSetTextMatrix

/** Lays text out with Core Text and draws it into a bitmap, on the iOS simulator. */
class CoreTextTypesetterTest {
    private val typesetter = CoreTextTypesetter(EnglishLocaleFormats, false, "en")
    private val words = (1..60).joinToString(" ") { "word$it" }

    @Test
    fun shortText_isOneLine() {
        val text = typesetter.layOut("Camping", ReportStyle.Body, 300)

        assertEquals(1, text.lineCount)
        assertEquals(0, text.lineStart(0))
        assertEquals(listOf("Camping"), text.lines())
    }

    // Core Text gives it no lines, but ReportText asks for one, as StaticLayout gives it.
    @Test
    fun emptyText_isOneEmptyLine_asTallAsALine() {
        val text = typesetter.layOut("", ReportStyle.Body, 300)

        assertEquals(1, text.lineCount)
        assertEquals(0, text.lineStart(0))
        assertEquals(typesetter.layOut("Camping", ReportStyle.Body, 300).height, text.height)
    }

    @Test
    fun longText_breaksIntoLines_withEveryWordOnce_inOrder() {
        val text = typesetter.layOut(words, ReportStyle.Body, 200)

        assertTrue(text.lineCount > 1, "Expected several lines, got ${text.lineCount}")
        assertEquals(words.split(" "), text.lines().flatMap { it.split(" ") })
    }

    // Notes typed in scripts that break lines without spaces, or are written right to left.
    @Test
    fun longTextInOtherScripts_breaksIntoLinesWithinTheWidth_withEveryCharacterOnce() {
        val notes = listOf(
            "我们在湖边露营了三个晚上，学会了搭帐篷、生火和辨认星座。",
            "เราไปตั้งแคมป์ที่ทะเลสาบสามคืนและเรียนรู้การกางเต็นท์",
            "ما سه شب کنار دریاچه کمپ زدیم و برپا کردن چادر را یاد گرفتیم. "
        ).map { it.repeat(6) }

        notes.forEach { note ->
            val text = typesetter.layOut(note, ReportStyle.Body, 200)

            val ink = ink { text.draw(it, 50f, 60f) }
            assertTrue(text.lineCount > 1, "Expected several lines for \"${note.take(10)}…\"")
            assertEquals(
                note.filterNot { it.isWhitespace() },
                text.lines().joinToString("").filterNot { it.isWhitespace() }
            )
            assertTrue(ink.found, "Nothing was drawn")
            assertTrue(
                ink.left >= 50 && ink.right < 50 + 200,
                "Ink is at x ${ink.left}–${ink.right}"
            )
            assertTrue(ink.bottom < 60 + text.height, "Ink ends at y ${ink.bottom}")
        }
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
        }
    }

    // Core Text would leave the lines that don't fit its frame out, and the report with them.
    @Test
    fun textTallerThanCoreTextsFrame_failsRatherThanLosingLines() {
        val lines = "\n".repeat(100_000)

        val failure = runCatching { typesetter.layOut(lines, ReportStyle.Body, 200) }

        assertTrue(failure.exceptionOrNull() is IllegalStateException, "$failure")
    }

    @Test
    fun text_isDrawnAtItsPlace_withinItsWidthAndHeight() {
        val text = typesetter.layOut(words, ReportStyle.Body, 200)

        val ink = ink { text.draw(it, 50f, 60f) }

        assertTrue(ink.found, "Nothing was drawn")
        assertTrue(ink.left >= 50, "Ink starts at x ${ink.left}")
        assertTrue(ink.right < 50 + 200, "Ink ends at x ${ink.right}")
        assertTrue(ink.top >= 60, "Ink starts at y ${ink.top}")
        assertTrue(ink.bottom < 60 + text.height, "Ink ends at y ${ink.bottom}")
    }

    // The text matrix isn't part of the graphics state, which saving and restoring it leaves out.
    @Test
    fun draw_leavesTheContextsTextMatrixAsItWas() {
        val text = typesetter.layOut("Camping", ReportStyle.Body, 200)
        var after: CValue<CGAffineTransform>? = null

        ink { context ->
            CGContextSetTextMatrix(context, CGAffineTransformMakeScale(2.0, 3.0))
            text.draw(context, 50f, 60f)
            after = CGContextGetTextMatrix(context)
        }

        after!!.useContents {
            assertEquals(listOf(2.0, 0.0, 0.0, 3.0, 0.0, 0.0), listOf(a, b, c, d, tx, ty))
        }
    }

    @Test
    fun leftToRight_isDrawnFromTheLeft() {
        val text = typesetter.layOut("Camping", ReportStyle.Body, 200)

        val ink = ink { text.draw(it, 50f, 60f) }

        assertTrue(ink.found, "Nothing was drawn")
        assertTrue(ink.left - 50 < 50 + 200 - ink.right, "Ink is at x ${ink.left}–${ink.right}")
    }

    @Test
    fun rightToLeft_isDrawnFromTheRight() {
        val rightToLeft = CoreTextTypesetter(EnglishLocaleFormats, true, "en")
        val text = rightToLeft.layOut("Camping", ReportStyle.Body, 200)

        val ink = ink { text.draw(it, 50f, 60f) }

        assertTrue(ink.found, "Nothing was drawn")
        assertTrue(ink.left - 50 > 50 + 200 - ink.right, "Ink is at x ${ink.left}–${ink.right}")
        assertTrue(ink.right < 50 + 200, "Ink ends at x ${ink.right}")
    }

    @Test
    fun centered_isDrawnInTheMiddle() {
        val text = typesetter.layOut("Page 1 of 2", ReportStyle.Footer, 200, centered = true)

        val ink = ink { text.draw(it, 50f, 60f) }

        assertTrue(ink.found, "Nothing was drawn")
        // Pixels round, and the first and last letters' ink doesn't fill their whole width, so
        // the space on each side can differ by a point or two.
        val left = ink.left - 50
        val right = 50 + 200 - 1 - ink.right
        assertTrue(abs(left - right) <= 2, "Space on the left: $left, on the right: $right")
    }

    @Test
    fun boldStyle_drawsMoreInk_thanBody() {
        val bold = typesetter.layOut("Camping", ReportStyle.Subheading, 300)
        val body = typesetter.layOut("Camping", ReportStyle.Body, 300)

        val boldInk = ink { bold.draw(it, 50f, 60f) }
        val bodyInk = ink { body.draw(it, 50f, 60f) }

        assertTrue(
            boldInk.amount > bodyInk.amount,
            "Bold ${boldInk.amount}, body ${bodyInk.amount}"
        )
    }

    // Kanji that Chinese and Japanese draw differently, which the language picks between.
    @Test
    fun language_picksTheGlyphs() {
        fun drawn(language: String): Ink {
            val text = CoreTextTypesetter(EnglishLocaleFormats, false, language)
                .layOut("直骨", ReportStyle.Title, 300)
            return ink { text.draw(it, 50f, 60f) }
        }

        val japanese = drawn("ja")
        val chinese = drawn("zh-Hans")

        assertTrue(japanese.found && chinese.found, "Nothing was drawn")
        assertFalse(
            japanese.amount == chinese.amount && japanese.left == chinese.left &&
                japanese.right == chinese.right,
            "Japanese and Chinese drew the same"
        )
    }
}
