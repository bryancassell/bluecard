package io.github.bryancassell.bluecard.data.progress

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConvertersTest {
    private val converters = Converters()

    // Databases already hold dates in this form, so it can't change without a migration.
    @Test
    fun date_isStoredAsYearMonthDay() {
        assertEquals("2026-04-05", converters.localDateToString(LocalDate(2026, 4, 5)))
    }

    @Test
    fun storedYearMonthDay_isItsDate() {
        assertEquals(LocalDate(2026, 4, 5), converters.stringToLocalDate("2026-04-05"))
    }

    @Test
    fun noDate_isStoredAsNull() {
        assertNull(converters.localDateToString(null))
        assertNull(converters.stringToLocalDate(null))
    }
}
