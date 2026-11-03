@file:OptIn(ExperimentalForeignApi::class)

package io.github.bryancassell.bluecard.data.report

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
    override val rightToLeftTypesetter = CoreTextTypesetter(usEnglish, true, "en")

    override fun ink(text: CoreTextText, x: Float, y: Float): Ink =
        bitmapInk { text.draw(it, x, y) }

    @Test
    fun longTextInOtherScripts_isDrawnWithinTheWidth() {
        otherScripts.forEach { note ->
            val text = typesetter.layOut(note, ReportStyle.Body, 200)

            val ink = ink(text, 50f, 60f)

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

    // The text matrix isn't part of the graphics state, which saving and restoring it leaves out.
    @Test
    fun draw_leavesTheContextsTextMatrixAsItWas() {
        val text = typesetter.layOut("Camping", ReportStyle.Body, 200)
        var after: CValue<CGAffineTransform>? = null

        bitmapInk { context ->
            CGContextSetTextMatrix(context, CGAffineTransformMakeScale(2.0, 3.0))
            text.draw(context, 50f, 60f)
            after = CGContextGetTextMatrix(context)
        }

        after!!.useContents {
            assertEquals(listOf(2.0, 0.0, 0.0, 3.0, 0.0, 0.0), listOf(a, b, c, d, tx, ty))
        }
    }

    // Kanji that Chinese and Japanese draw differently, which the language picks between.
    @Test
    fun language_picksTheGlyphs() {
        fun drawn(language: String): Ink {
            val text = CoreTextTypesetter(usEnglish, false, language)
                .layOut("直骨", ReportStyle.Title, 300)
            return ink(text, 50f, 60f)
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
