package io.github.bryancassell.bluecard.data.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavior every [ReportTypesetter] must have, which report paging relies on. Runs against
 * Android's (StaticLayoutTypesetterTest) and iOS's (CoreTextTypesetterTest), which also check
 * where each draws its text.
 */
abstract class ReportTypesetterContract<T : ReportText> {
    /** A typesetter in US English. */
    protected abstract val typesetter: ReportTypesetter<T>

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

    companion object {
        /** Long notes in Chinese, Thai and Persian. */
        val otherScripts = listOf(
            "我们在湖边露营了三个晚上，学会了搭帐篷、生火和辨认星座。",
            "เราไปตั้งแคมป์ที่ทะเลสาบสามคืนและเรียนรู้การกางเต็นท์",
            "ما سه شب کنار دریاچه کمپ زدیم و برپا کردن چادر را یاد گرفتیم. "
        ).map { it.repeat(6) }
    }
}
