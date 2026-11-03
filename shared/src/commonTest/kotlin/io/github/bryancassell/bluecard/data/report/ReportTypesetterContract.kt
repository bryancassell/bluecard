package io.github.bryancassell.bluecard.data.report

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavior every [ReportTypesetter] must have: how it breaks lines, which report paging relies
 * on, and where its text is drawn. Runs against Android's (StaticLayoutTypesetterTest) and iOS's
 * (CoreTextTypesetterTest).
 */
abstract class ReportTypesetterContract<T : ReportText> {
    /** A typesetter in US English. */
    protected abstract val typesetter: ReportTypesetter<T>

    /** A typesetter for a locale written right to left. */
    protected abstract val rightToLeftTypesetter: ReportTypesetter<T>

    /**
     * Draws [text] with its top left corner at ([x], [y]) on a white page, [PAGE_WIDTH] by
     * [PAGE_HEIGHT] pixels, one a point, as the platform's PDF writer draws it, and finds where
     * it's dark.
     */
    protected abstract fun ink(text: T, x: Float, y: Float): Ink

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
    fun longTextInOtherScripts_breaksIntoLines_withEveryCharacterOnce() {
        otherScripts.forEach { note ->
            val text = typesetter.layOut(note, ReportStyle.Body, 200)

            assertTrue(text.lineCount > 1, "Expected several lines for \"${note.take(10)}…\"")
            assertEquals(
                note.filterNot { it.isWhitespace() },
                text.lines().joinToString("").filterNot { it.isWhitespace() }
            )
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

    @Test
    fun text_isDrawnAtItsPlace_withinItsWidthAndHeight() {
        val text = typesetter.layOut(words, ReportStyle.Body, 200)

        val ink = ink(text, 50f, 60f)

        assertTrue(ink.found, "Nothing was drawn")
        assertTrue(ink.left >= 50, "Ink starts at x ${ink.left}")
        assertTrue(ink.right < 50 + 200, "Ink ends at x ${ink.right}")
        assertTrue(ink.top >= 60, "Ink starts at y ${ink.top}")
        assertTrue(ink.bottom < 60 + text.height, "Ink ends at y ${ink.bottom}")
    }

    @Test
    fun leftToRight_isDrawnFromTheLeft() {
        val ink = ink(typesetter.layOut("Camping", ReportStyle.Body, 200), 50f, 60f)

        assertTrue(ink.found, "Nothing was drawn")
        assertTrue(ink.left - 50 < 50 + 200 - ink.right, "Ink is at x ${ink.left}–${ink.right}")
    }

    @Test
    fun rightToLeft_isDrawnFromTheRight() {
        val text = rightToLeftTypesetter.layOut("Camping", ReportStyle.Body, 200)

        val ink = ink(text, 50f, 60f)

        assertTrue(ink.found, "Nothing was drawn")
        assertTrue(ink.left - 50 > 50 + 200 - ink.right, "Ink is at x ${ink.left}–${ink.right}")
        assertTrue(ink.right < 50 + 200, "Ink ends at x ${ink.right}")
    }

    @Test
    fun centered_isDrawnInTheMiddle() {
        val text = typesetter.layOut("Page 1 of 2", ReportStyle.Footer, 200, centered = true)

        val ink = ink(text, 50f, 60f)

        assertTrue(ink.found, "Nothing was drawn")
        // Pixels round, and the first and last letters' ink doesn't fill their whole width, so
        // the space on each side can differ by a point or two.
        val left = ink.left - 50
        val right = 50 + 200 - 1 - ink.right
        assertTrue(abs(left - right) <= 2, "Space on the left: $left, on the right: $right")
    }

    @Test
    fun boldStyle_drawsMoreInk_thanBody() {
        val bold = ink(typesetter.layOut("Camping", ReportStyle.Subheading, 300), 50f, 60f)
        val body = ink(typesetter.layOut("Camping", ReportStyle.Body, 300), 50f, 60f)

        assertTrue(bold.amount > body.amount, "Bold ${bold.amount}, body ${body.amount}")
    }

    companion object {
        /** Long notes in Chinese, Thai and Persian. */
        val otherScripts = listOf(
            "我们在湖边露营了三个晚上，学会了搭帐篷、生火和辨认星座。",
            "เราไปตั้งแคมป์ที่ทะเลสาบสามคืนและเรียนรู้การกางเต็นท์",
            "ما سه شب کنار دریاچه کمپ زدیم و برپا کردن چادر را یاد گرفتیم. "
        ).map { it.repeat(6) }
    }
}
