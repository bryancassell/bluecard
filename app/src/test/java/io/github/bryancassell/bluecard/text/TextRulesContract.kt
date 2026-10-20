package io.github.bryancassell.bluecard.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Behavior every [TextRules] must have. Runs against Android's (AndroidTextRulesTest), and is for
 * the iOS one to run too (#350). Unlike the repositories, it has no fake: other tests use
 * Android's, which runs in local tests, so what they check is what the app does.
 */
abstract class TextRulesContract {
    protected abstract val textRules: TextRules

    @Test
    fun englishOrder_ignoresCaseAndAccents() {
        assertEquals(
            listOf("bird Study", "Écologie", "Zoology"),
            listOf("Zoology", "Écologie", "bird Study").sortedWith(textRules.englishOrder())
        )
    }

    // So names that differ only in case still sort the same way every time.
    @Test
    fun englishOrder_breaksTiesByCase() {
        val order = textRules.englishOrder()

        assertNotEquals(0, order.compare("bird study", "Bird Study"))
        assertEquals(
            -order.compare("bird study", "Bird Study"),
            order.compare("Bird Study", "bird study")
        )
    }

    @Test
    fun characterBoundaries_areBetweenEachLetter() {
        assertEquals(listOf(0, 1, 2, 3), textRules.characterBoundaries("abc"))
    }

    @Test
    fun characterBoundaries_ofNoText_areJustItsStart() {
        assertEquals(listOf(0), textRules.characterBoundaries(""))
    }

    @Test
    fun characterBoundaries_keepALetterWithItsAccentMark() {
        // "e" and a combining acute accent, which read as "é".
        assertEquals(listOf(0, 2, 3), textRules.characterBoundaries("éx"))
    }

    @Test
    fun characterBoundaries_keepEachEmojiWhole() {
        // A grinning face (two UTF-16 units), a flag (two regional indicators, four units) and a
        // thumbs-up with a skin tone (two code points, four units).
        val emoji = "😀🇺🇸👍🏽"

        assertEquals(listOf(0, 2, 6, 10), textRules.characterBoundaries(emoji))
    }

    @Test
    fun lineBreaks_areAfterSpaces() {
        assertEquals(listOf(0, 9, 15, 20), textRules.lineBreaks("Swimming merit badge"))
    }

    @Test
    fun lineBreaks_ofOneWord_areJustItsEnds() {
        assertEquals(listOf(0, 8), textRules.lineBreaks("Swimming"))
    }

    @Test
    fun lineBreaks_includeAfterAHyphen() {
        assertEquals(listOf(0, 6, 14), textRules.lineBreaks("Eagle-required"))
    }
}
