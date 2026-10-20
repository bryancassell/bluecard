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

    /** The offsets in [text] that are boundaries between characters. */
    private fun characterBoundaries(text: String) =
        (0..text.length).filter { textRules.characterBoundaryAtOrBefore(text, it) == it }

    /** The offsets in [text] where a line may break. */
    private fun lineBreaks(text: String) =
        (0..text.length).filter { textRules.isLineBreak(text, it) }

    @Test
    fun characterBoundaries_areBetweenEachLetter() {
        assertEquals(listOf(0, 1, 2, 3), characterBoundaries("abc"))
    }

    @Test
    fun characterBoundaries_ofNoText_areJustItsStart() {
        assertEquals(listOf(0), characterBoundaries(""))
    }

    @Test
    fun characterBoundaries_keepALetterWithItsAccentMark() {
        // "e" and a combining acute accent, which read as "é".
        assertEquals(listOf(0, 2, 3), characterBoundaries("e\u0301x"))
    }

    // A grinning face (two UTF-16 units), a flag (two regional indicators, four units) and a
    // thumbs-up with a skin tone (two code points, four units).
    private val emoji = "\uD83D\uDE00\uD83C\uDDFA\uD83C\uDDF8\uD83D\uDC4D\uD83C\uDFFD"

    @Test
    fun characterBoundaries_keepEachEmojiWhole() {
        assertEquals(listOf(0, 2, 6, 10), characterBoundaries(emoji))
    }

    @Test
    fun characterBoundaryAtOrBefore_insideACharacter_isAtItsStart() {
        assertEquals(2, textRules.characterBoundaryAtOrBefore(emoji, 4))
        assertEquals(6, textRules.characterBoundaryAtOrBefore(emoji, 9))
    }

    @Test
    fun characterBoundaryAtOrAfter_insideACharacter_isAtItsEnd() {
        assertEquals(6, textRules.characterBoundaryAtOrAfter(emoji, 4))
        assertEquals(10, textRules.characterBoundaryAtOrAfter(emoji, 9))
    }

    @Test
    fun characterBoundaryAtOrAfter_atABoundary_isThere() {
        assertEquals(2, textRules.characterBoundaryAtOrAfter(emoji, 2))
        assertEquals(10, textRules.characterBoundaryAtOrAfter(emoji, 10))
    }

    @Test
    fun lineBreaks_areAfterSpaces() {
        assertEquals(listOf(0, 9, 15, 20), lineBreaks("Swimming merit badge"))
    }

    @Test
    fun lineBreaks_ofOneWord_areJustItsEnds() {
        assertEquals(listOf(0, 8), lineBreaks("Swimming"))
    }

    @Test
    fun lineBreaks_includeAfterAHyphen() {
        assertEquals(listOf(0, 6, 14), lineBreaks("Eagle-required"))
    }
}
