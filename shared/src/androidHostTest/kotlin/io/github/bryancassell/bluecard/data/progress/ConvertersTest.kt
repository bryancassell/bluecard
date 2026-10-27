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

    // A value these can't read sets aside all the scout's progress, so a library update that
    // can't read what it wrote must fail here first.
    @Test
    fun unusualDates_areReadBack() {
        val dates = listOf(LocalDate(1, 1, 1), LocalDate(9999, 12, 31), LocalDate(2028, 2, 29))

        val readBack = dates.map { converters.stringToLocalDate(converters.localDateToString(it)) }

        assertEquals(dates, readBack)
        assertEquals(emptyList<IllegalArgumentException>(), unreadable)
    }

    @Test
    fun unusualTrackerValues_areReadBack() {
        val values = mapOf(
            "Quotes" to "\"a\" \\ b / c",
            "Control" to "line\nbreak\ttab\r\u0000\u001f",
            "Non-ASCII" to "Café Ñandú 日本 ‏",
            "Emoji" to "🏕️ 👨‍👧",
            "Lone surrogates" to "\uD83D \uDC22 \uD83D🏕 \uDFD5\uDFD5 \uD83D",
            "" to "",
            "{\"key\": [1]}" to "{}"
        )

        val readBack = converters.jsonToMap(converters.mapToJson(values))

        assertEquals(values, readBack)
        assertEquals(emptyList<IllegalArgumentException>(), unreadable)
    }

    @Test
    fun loneSurrogates_areStoredEscaped_andPairsAsTheyAre() {
        val stored = converters.mapToJson(mapOf("Notes" to "\uD83D \uDC22 🏕"))

        assertEquals("""{"Notes":"\ud83d \udc22 ${"🏕"}"}""", stored)
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
