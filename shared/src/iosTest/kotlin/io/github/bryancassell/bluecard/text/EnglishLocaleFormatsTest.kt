package io.github.bryancassell.bluecard.text

import io.github.artificialpb.bignum.bigDecimalOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

// LocaleFormatsContract's cases in US English, which this writes as Android's formatters do.
class EnglishLocaleFormatsTest {
    private val formats = EnglishLocaleFormats

    @Test
    fun formatDate_isWrittenOut() {
        assertEquals("Apr 15, 2026", formats.formatDate(LocalDate(2026, 4, 15)))
    }

    @Test
    fun formatDate_hasNoLeadingZero() {
        assertEquals("Sep 5, 2026", formats.formatDate(LocalDate(2026, 9, 5)))
    }

    private fun total(text: String) = formats.formatTotal(bigDecimalOf(text))

    @Test
    fun formatTotal_writesUpToTwoDecimalPlaces_withoutTrailingZeros() {
        assertEquals("4.25", total("4.25"))
        assertEquals("4.5", total("4.50"))
        assertEquals("2", total("2.0"))
    }

    // Trailing zeros come off as an exponent, 1E+2, which the total is written without.
    @Test
    fun formatTotal_ofTens_isWrittenOut() {
        assertEquals("100", total("100"))
        assertEquals("0", total("0.00"))
    }

    @Test
    fun formatTotal_roundsDown() {
        assertEquals("5.99", total("5.999"))
        assertEquals("4.33", total("4.3333333333"))
    }

    @Test
    fun formatTotal_doesntGroupDigits() {
        assertEquals("1250.5", total("1250.5"))
    }

    @Test
    fun formatList_joinsTheItemsInASentence() {
        assertEquals("Swimming", formats.formatList(listOf("Swimming")))
        assertEquals("Hiking and Swimming", formats.formatList(listOf("Hiking", "Swimming")))
        assertEquals(
            "Cycling, Hiking, and Swimming",
            formats.formatList(listOf("Cycling", "Hiking", "Swimming"))
        )
    }

    @Test
    fun typedText_inAnotherDirection_isWrappedToKeepIt() {
        assertEquals("\u200E\u202Bعلی.\u202C\u200E", formats.typedText("علی."))
        assertEquals("\u200E\u202Bשרה\u202C\u200E", formats.typedText("שרה"))
    }

    // Digits and punctuation have no direction of their own, so the first letter decides.
    @Test
    fun typedText_startingWithDigits_takesItsDirectionFromItsFirstLetter() {
        assertEquals("\u200E\u202B12 علی\u202C\u200E", formats.typedText("12 علی"))
        assertEquals("12 Alex", formats.typedText("12 Alex"))
    }

    @Test
    fun typedText_endingInAnotherDirection_isFollowedByAMark() {
        assertEquals("Alex علی\u200E", formats.typedText("Alex علی"))
    }

    // A right-to-left mark sets the direction as a right-to-left letter does, as in Android's
    // BidiFormatter.
    @Test
    fun typedText_startingWithARightToLeftMark_isWrappedToKeepIt() {
        val text = text(0x200F) + "Alex"

        assertEquals(wrapped(text), formats.typedText(text))
    }

    // Adlam's letters are beyond the Basic Multilingual Plane, as two UTF-16 units each.
    @Test
    fun typedText_inAScriptBeyondTheBasicPlane_isWrappedToKeepIt() {
        val text = text(0x1E900, 0x1E922, 0x1E92A)

        assertEquals(wrapped(text), formats.typedText(text))
    }

    private fun text(vararg codePoints: Int) = codePoints.joinToString("") {
        if (it <= 0xFFFF) {
            it.toChar().toString()
        } else {
            val offset = it - 0x10000
            charArrayOf((0xD800 + (offset shr 10)).toChar(), (0xDC00 + (offset and 0x3FF)).toChar())
                .concatToString()
        }
    }

    // Between left-to-right marks, embedded as right-to-left.
    private fun wrapped(text: String) = text(0x200E, 0x202B) + text + text(0x202C, 0x200E)

    @Test
    fun typedText_inTheLocalesDirection_isUnchanged() {
        assertEquals("Alex Scout", formats.typedText("Alex Scout"))
        assertEquals("555-0100", formats.typedText("555-0100"))
    }
}
