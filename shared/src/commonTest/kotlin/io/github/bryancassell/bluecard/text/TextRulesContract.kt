package io.github.bryancassell.bluecard.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Behavior every [TextRules] must have. Runs against Android's (AndroidTextRulesTest) and iOS's
 * (IosTextRulesTest). Unlike the repositories, it has no fake: other tests use the real ones,
 * Android's or, in common tests, each platform's (platformTextRules), so what they check is what
 * the app does, apart from Android's sort order: local tests sort with the JDK's collator, not
 * the ICU one Android's is built on.
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
    fun englishOrder_putsLowercaseFirst() {
        assertEquals(
            listOf("bird study", "Bird Study"),
            listOf("Bird Study", "bird study").sortedWith(textRules.englishOrder())
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
    fun lineBreaks_areNeverAfterANoBreakSpace() {
        assertEquals(listOf(0, 8), lineBreaks("6${0xA0.toChar()}nights"))
    }

    @Test
    fun lineBreaks_keepANumberRangeWhole() {
        assertEquals(listOf(0, 6, 12), lineBreaks("10-12 nights"))
    }

    @Test
    fun lineBreaks_includeAroundAnEmDash() {
        assertEquals(listOf(0, 7, 8, 16), lineBreaks("Camping${0x2014.toChar()}Advanced"))
    }

    @Test
    fun lineBreaks_includeAfterAHyphen() {
        assertEquals(listOf(0, 6, 14), lineBreaks("Eagle-required"))
    }

    @Test
    fun lineBreaks_afterSeveralSpaces_areBeforeTheNextWord() {
        assertEquals(listOf(0, 4, 6), lineBreaks("Go  on"))
    }

    @Test
    fun isLetterOrDigit_isTrueForLettersAndDecimalDigits() {
        assertTrue(textRules.isLetterOrDigit('a'.code))
        assertTrue(textRules.isLetterOrDigit('7'.code))
        // An Arabic letter and an Arabic-Indic digit.
        assertTrue(textRules.isLetterOrDigit(0x0639))
        assertTrue(textRules.isLetterOrDigit(0x0663))
        // A CJK letter beyond the Basic Multilingual Plane.
        assertTrue(textRules.isLetterOrDigit(0x20000))
    }

    @Test
    fun isLetterOrDigit_isFalseForEverythingElse() {
        assertFalse(textRules.isLetterOrDigit(' '.code))
        assertFalse(textRules.isLetterOrDigit('.'.code))
        // A combining acute accent, which is a mark, not a letter.
        assertFalse(textRules.isLetterOrDigit(0x0301))
        // A superscript two, which is a number, but not a decimal digit.
        assertFalse(textRules.isLetterOrDigit(0x00B2))
        // A grinning face, a symbol beyond the Basic Multilingual Plane.
        assertFalse(textRules.isLetterOrDigit(0x1F600))
    }
}
