package io.github.bryancassell.bluecard.text

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

// Robolectric, because BidiFormatter reads the locale's direction from Android's TextUtils.
@RunWith(AndroidJUnit4::class)
class FormattingTest {
    private val day = LocalDate.of(2026, 4, 15)

    @Test
    fun completionDateFormatter_writesTheDateOut() {
        assertEquals("Apr 15, 2026", completionDateFormatter(Locale.US).format(day))
    }

    // A device in the strings' language keeps its choice of digits (stringsLocales).
    @Test
    fun completionDateFormatter_usesTheLocalesDigits() {
        val locale = Locale.forLanguageTag("en-US-u-nu-arab")

        assertEquals("Apr ١٥, ٢٠٢٦", completionDateFormatter(locale).format(day))
    }

    private fun total(text: String, locale: Locale = Locale.US) =
        totalFormat(locale).format(BigDecimal(text))

    @Test
    fun totalFormat_writesUpToTwoDecimalPlaces_withoutTrailingZeros() {
        assertEquals("4.25", total("4.25"))
        assertEquals("4.5", total("4.50"))
        // 1.5 + 0.5 is 2.0.
        assertEquals("2", total("2.0"))
    }

    // So it never reads as the amount needed before it's reached.
    @Test
    fun totalFormat_roundsDown() {
        assertEquals("5.99", total("5.999"))
        assertEquals("4.33", total("4.3333333333"))
    }

    // Like the amount needed after it, and a comma can be what the scout typed as a decimal
    // separator.
    @Test
    fun totalFormat_doesntGroupDigits() {
        assertEquals("1250.5", total("1250.5"))
    }

    @Test
    fun totalFormat_usesTheLocalesDigitsAndSeparator() {
        assertEquals("٤٫٥", total("4.5", Locale.forLanguageTag("en-US-u-nu-arab")))
    }

    // A Persian name in an English sentence keeps its period at its end.
    @Test
    fun typedText_inAnotherDirection_isWrappedToKeepIt() {
        assertEquals("\u200E\u202Bعلی.\u202C\u200E", typedText("علی.", Locale.US))
    }

    @Test
    fun typedText_inTheStringsDirection_isUnchanged() {
        assertEquals("Alex Scout", typedText("Alex Scout", Locale.US))
    }
}
