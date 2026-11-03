@file:OptIn(ExperimentalForeignApi::class)

package io.github.bryancassell.bluecard.data.report

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

/**
 * Lays text out with Core Text, and draws it into a bitmap, on the iOS simulator. What every
 * typesetter must do is in [ReportTypesetterContract].
 */
class CoreTextTypesetterTest : ReportTypesetterContract<CoreTextText>() {
    override val typesetter = CoreTextTypesetter(usEnglish, false, "en")
    private val words = (1..60).joinToString(" ") { "word$it" }

    @Test
    fun longTextInOtherScripts_isDrawnWithinTheWidth() {
        otherScripts.forEach { note ->
            val text = typesetter.layOut(note, ReportStyle.Body, 200)

            val ink = ink { text.draw(it, 50f, 60f) }

            assertTrue(ink.found, "Nothing was drawn")
            assertTrue(
                ink.left >= 50 && ink.right < 50 + 200,
                "Ink is at x ${ink.left}–${ink.right}"
            )
            assertTrue(ink.bottom < 60 + text.height, "Ink ends at y ${ink.bottom}")
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
        val rightToLeft = CoreTextTypesetter(usEnglish, true, "en")
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
            val text = CoreTextTypesetter(usEnglish, false, language)
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
