package io.github.bryancassell.bluecard.text

import io.github.artificialpb.bignum.bigDecimalOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

/**
 * Behavior every [LocaleFormats] must have. Runs against Android's (AndroidLocaleFormatsTest) and
 * iOS's (IosLocaleFormatsTest). Unlike the repositories, it has no fake: other tests use
 * Android's, which runs in local tests, so what they check is what the app shows.
 */
abstract class LocaleFormatsContract {
    /** The formats for the locale with the BCP 47 tag [languageTag]. */
    protected abstract fun formats(languageTag: String): LocaleFormats

    private val day = LocalDate(2026, 4, 15)

    @Test
    fun formatDate_isWrittenOut() {
        assertEquals("Apr 15, 2026", formats("en-US").formatDate(day))
    }

    // A device in the strings' language keeps its choice of digits (stringsLocales).
    @Test
    fun formatDate_usesTheLocalesDigits() {
        assertEquals("Apr ١٥, ٢٠٢٦", formats("en-US-u-nu-arab").formatDate(day))
    }

    private fun total(text: String, languageTag: String = "en-US") =
        formats(languageTag).formatTotal(bigDecimalOf(text))

    @Test
    fun formatTotal_writesUpToTwoDecimalPlaces_withoutTrailingZeros() {
        assertEquals("4.25", total("4.25"))
        assertEquals("4.5", total("4.50"))
        // 1.5 + 0.5 is 2.0.
        assertEquals("2", total("2.0"))
    }

    // So it never reads as the amount needed before it's reached.
    @Test
    fun formatTotal_roundsDown() {
        assertEquals("5.99", total("5.999"))
        assertEquals("4.33", total("4.3333333333"))
    }

    // In binary floating point, 0.29 is a little under it, which rounding down would write as 0.28.
    @Test
    fun formatTotal_withTwoDecimalPlaces_isWrittenExactly() {
        assertEquals("0.29", total("0.29"))
    }

    // More digits than a double holds, which would round it up to 10000000000000000.
    @Test
    fun formatTotal_withManyDigits_isWrittenExactly() {
        assertEquals("9999999999999999.99", total("9999999999999999.99"))
    }

    // Like the amount needed after it, and a comma can be what the scout typed as a decimal
    // separator.
    @Test
    fun formatTotal_doesntGroupDigits() {
        assertEquals("1250.5", total("1250.5"))
    }

    @Test
    fun formatTotal_usesTheLocalesDigitsAndSeparator() {
        assertEquals("٤٫٥", total("4.5", "en-US-u-nu-arab"))
    }

    @Test
    fun formatList_joinsTheItemsInASentence() {
        val formats = formats("en-US")

        assertEquals("Swimming", formats.formatList(listOf("Swimming")))
        assertEquals("Hiking and Swimming", formats.formatList(listOf("Hiking", "Swimming")))
        assertEquals(
            "Cycling, Hiking, and Swimming",
            formats.formatList(listOf("Cycling", "Hiking", "Swimming"))
        )
    }

    @Test
    fun formatList_followsTheLocalesRules() {
        assertEquals(
            "Cycling, Hiking and Swimming",
            formats("en-GB").formatList(listOf("Cycling", "Hiking", "Swimming"))
        )
    }

    // A Persian name in an English sentence keeps its period at its end.
    @Test
    fun typedText_inAnotherDirection_isWrappedToKeepIt() {
        assertEquals("\u200E\u202Bعلی.\u202C\u200E", formats("en-US").typedText("علی."))
    }

    // As an English name would in a right-to-left translation's sentence.
    @Test
    fun typedText_leftToRight_inARightToLeftLocale_isWrappedToKeepIt() {
        assertEquals("\u200F\u202AAlex.\u202C\u200F", formats("fa").typedText("Alex."))
    }

    // So what follows it in a right-to-left translation's sentence isn't drawn into it.
    @Test
    fun typedText_endingInAnotherDirection_inARightToLeftLocale_isFollowedByAMark() {
        assertEquals("علی Alex\u200F", formats("fa").typedText("علی Alex"))
    }

    // Text with no letters, such as a phone number, counts as left-to-right.
    @Test
    fun typedText_withNoLetters_inARightToLeftLocale_isWrappedAsLeftToRight() {
        assertEquals("\u200F\u202A555-0100\u202C\u200F", formats("fa").typedText("555-0100"))
    }

    // Punjabi is written left-to-right in Gurmukhi, but right-to-left in its Arabic script.
    @Test
    fun typedText_inALocaleWhoseScriptIsRightToLeft_isWrappedAsInARightToLeftLocale() {
        assertEquals("\u200F\u202AAlex.\u202C\u200F", formats("pa-Arab").typedText("Alex."))
    }

    // U+20000, a CJK letter beyond the Basic Multilingual Plane, is left-to-right, so it decides
    // the direction rather than the Persian name after it.
    @Test
    fun typedText_startingWithALetterBeyondTheBasicPlane_takesItsDirection() {
        assertEquals("\uD840\uDC00علی\u200E", formats("en-US").typedText("\uD840\uDC00علی"))
    }

    // A right-to-left embedding sets the direction as a right-to-left letter does.
    @Test
    fun typedText_startingWithARightToLeftEmbedding_isWrappedToKeepIt() {
        assertEquals("\u200E\u202B\u202BAlex\u202C\u200E", formats("en-US").typedText("\u202BAlex"))
    }

    // BidiFormatter decides whether to embed by the first UTF-16 unit with a direction, and a
    // surrogate's is left-to-right, so text that starts beyond the Basic Multilingual Plane isn't
    // embedded. The marks around it go by code point, so this Persian name still gets them.
    @Test
    fun typedText_startingWithAnEmoji_isntEmbedded() {
        assertEquals("\u200E\uD83D\uDE00علی\u200E", formats("en-US").typedText("\uD83D\uDE00علی"))
    }

    // Adlam, a right-to-left script beyond the Basic Multilingual Plane.
    @Test
    fun typedText_inAScriptBeyondTheBasicPlane_isBetweenMarks() {
        assertEquals(
            "\u200E\uD83A\uDD00\uD83A\uDD22\uD83A\uDD2A\u200E",
            formats("en-US").typedText("\uD83A\uDD00\uD83A\uDD22\uD83A\uDD2A")
        )
    }

    @Test
    fun typedText_inTheLocalesDirection_isUnchanged() {
        assertEquals("Alex Scout", formats("en-US").typedText("Alex Scout"))
    }
}
