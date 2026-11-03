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

    @Test
    fun typedText_inTheLocalesDirection_isUnchanged() {
        assertEquals("Alex Scout", formats("en-US").typedText("Alex Scout"))
    }
}
