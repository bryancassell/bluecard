package io.github.bryancassell.bluecard.text

import io.github.artificialpb.bignum.bigDecimalOf
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Behavior every [LocaleFormats] must have. Runs against Android's (AndroidLocaleFormatsTest), and
 * is for the iOS one to run too (#350). Unlike the repositories, it has no fake: other tests use
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

    @Test
    fun typedText_inTheLocalesDirection_isUnchanged() {
        assertEquals("Alex Scout", formats("en-US").typedText("Alex Scout"))
    }

    @Test
    fun formatPhoneNumber_withoutACountryCode_followsUsRules() {
        val formats = formats("en-US")

        assertEquals("(555) 123-4567", formats.formatPhoneNumber("5551234567"))
        assertEquals("(555) 123-4567", formats.formatPhoneNumber("555.123.4567"))
        assertEquals("555-0100", formats.formatPhoneNumber("5550100"))
    }

    // Regrouped by the UK's rules, it would read 55 5123 4567.
    @Test
    fun formatPhoneNumber_followsUsRules_inEveryLocale() {
        assertEquals("(555) 123-4567", formats("en-GB").formatPhoneNumber("5551234567"))
    }

    @Test
    fun formatPhoneNumber_withACountryCode_followsItsCountrysRules() {
        val formats = formats("en-US")

        assertEquals("+1 555-123-4567", formats.formatPhoneNumber("+15551234567"))
        assertEquals("+44 20 7946 0958", formats.formatPhoneNumber("+442079460958"))
    }

    @Test
    fun formatPhoneNumber_thatCantBeFormatted_isAsTyped() {
        val formats = formats("en-US")

        // A UK number without its country code.
        assertEquals("020 7946 0958", formats.formatPhoneNumber("020 7946 0958"))
        assertEquals("Ask at the office", formats.formatPhoneNumber("Ask at the office"))
    }
}
