package io.github.bryancassell.bluecard.data.progress

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ConvertersTest {
    private val unreadable = mutableListOf<IllegalArgumentException>()
    private val converters = Converters { unreadable += it }

    // Databases already hold dates in this form, so it can't change without a migration.
    @Test
    fun date_isStoredAsYearMonthDay() {
        assertEquals("2026-04-05", converters.localDateToString(LocalDate(2026, 4, 5)))
    }

    @Test
    fun storedYearMonthDay_isItsDate() {
        assertEquals(LocalDate(2026, 4, 5), converters.stringToLocalDate("2026-04-05"))
        assertEquals(emptyList<IllegalArgumentException>(), unreadable)
    }

    @Test
    fun noDate_isStoredAsNull() {
        assertNull(converters.localDateToString(null))
        assertNull(converters.stringToLocalDate(null))
    }

    @Test
    fun trackerValues_areStoredAsJson_andReadBack() {
        val values = mapOf("Distance" to "5", "Notes" to "Trail \"loop\"")

        val stored = converters.mapToJson(values)

        assertEquals("""{"Distance":"5","Notes":"Trail \"loop\""}""", stored)
        assertEquals(values, converters.jsonToMap(stored))
        assertEquals(emptyList<IllegalArgumentException>(), unreadable)
    }

    // The issue's example: a byte changed by storage, in a page SQLite still finds valid.
    @Test
    fun dateThatCantBeRead_isReported_andThrown() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            converters.stringToLocalDate("2026-0\u00011-01")
        }

        assertEquals(listOf(error), unreadable)
    }

    @Test
    fun trackerValuesThatCantBeRead_areReported_andThrown() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            converters.jsonToMap("""{"Distance":"5""")
        }

        assertEquals(listOf(error), unreadable)
    }
}
