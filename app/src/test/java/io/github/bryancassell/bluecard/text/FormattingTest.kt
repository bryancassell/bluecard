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

    @Test
    fun formatTotal_writesEveryDecimalPlaceTheValuesHave() {
        assertEquals("4.25", formatTotal(BigDecimal("4.25"), Locale.US))
        assertEquals("1,250", formatTotal(BigDecimal("1250"), Locale.US))
    }

    // 1.5 + 0.5 is 2.0.
    @Test
    fun formatTotal_leavesOutTrailingZeros() {
        assertEquals("2", formatTotal(BigDecimal("2.0"), Locale.US))
    }

    @Test
    fun formatTotal_usesTheLocalesDigitsAndSeparator() {
        val locale = Locale.forLanguageTag("en-US-u-nu-arab")

        assertEquals("٤٫٥", formatTotal(BigDecimal("4.5"), locale))
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
