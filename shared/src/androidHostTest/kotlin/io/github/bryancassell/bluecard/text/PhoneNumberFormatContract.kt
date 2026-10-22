package io.github.bryancassell.bluecard.text

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Behavior every [PhoneNumberFormat] must have. Runs against Android's
 * (AndroidPhoneNumberFormatTest), and is for the iOS one to run too (#350). Unlike the
 * repositories, it has no fake: other tests use Android's, which runs in local tests, so what they
 * check is what the app shows.
 */
abstract class PhoneNumberFormatContract {
    protected abstract val phoneNumberFormat: PhoneNumberFormat

    private suspend fun format(number: String) = phoneNumberFormat.format(number)

    @Test
    fun withoutACountryCode_followsUsRules() = runTest {
        assertEquals("(555) 123-4567", format("5551234567"))
        assertEquals("(555) 123-4567", format("555.123.4567"))
        assertEquals("555-0100", format("5550100"))
    }

    @Test
    fun withACountryCode_followsItsCountrysRules() = runTest {
        assertEquals("+1 555-123-4567", format("+15551234567"))
        assertEquals("+44 20 7946 0958", format("+442079460958"))
    }

    // Formatted, the words would be dropped, or an extension spelled differently.
    @Test
    fun withWordsInIt_isAsTyped() = runTest {
        assertEquals("Cell: 555-123-4567", format("Cell: 555-123-4567"))
        assertEquals("555-123-4567 hm", format("555-123-4567 hm"))
        assertEquals("555-123-4567 x12", format("555-123-4567 x12"))
    }

    @Test
    fun thatFormattingWouldJoinDigitsTypedApart_isAsTyped() = runTest {
        // US rules have no international format for 7 digits: +1 5550100.
        assertEquals("+1 555-0100", format("+1 555-0100"))
        // +1 555-123-4567, with the 1 and 23 typed apart joined.
        assertEquals("+1 5551 234567", format("+1 5551 234567"))
    }

    @Test
    fun thatCantBeFormatted_isAsTyped() = runTest {
        // A UK number without its country code.
        assertEquals("020 7946 0958", format("020 7946 0958"))
        // No country has the code 999.
        assertEquals("+999 123", format("+999 123"))
        assertEquals("Ask at the office", format("Ask at the office"))
    }
}
