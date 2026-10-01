package io.github.bryancassell.bluecard.text

import androidx.test.ext.junit.runners.AndroidJUnit4
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
