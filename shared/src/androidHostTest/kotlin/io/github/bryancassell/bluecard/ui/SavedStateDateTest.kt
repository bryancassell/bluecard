package io.github.bryancassell.bluecard.ui

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedStateDateTest {
    // The first and last dates LocalDate can hold. kotlinx-datetime keeps its MIN and MAX internal.
    private val first = LocalDate(-999_999_999, 1, 1)
    private val last = LocalDate(999_999_999, 12, 31)

    @Test
    fun epochDay_isItsDate() {
        val day = LocalDate(2026, 4, 15)

        assertEquals(day, dateFromEpochDay(day.toEpochDays()))
    }

    @Test
    fun firstAndLastDaysLocalDateCanHold_areDates() {
        assertEquals(first, dateFromEpochDay(first.toEpochDays()))
        assertEquals(last, dateFromEpochDay(last.toEpochDays()))
    }

    @Test
    fun null_isNoDate() {
        assertNull(dateFromEpochDay(null))
    }

    @Test
    fun valueOfAnotherKind_isIgnored() {
        assertNull(dateFromEpochDay("2026-04-15"))
        // An Int, not the Long an epoch day is kept as.
        assertNull(dateFromEpochDay(20_000))
    }

    @Test
    fun dayOutsideTheDatesLocalDateCanHold_isIgnored() {
        assertNull(dateFromEpochDay(first.toEpochDays() - 1))
        assertNull(dateFromEpochDay(last.toEpochDays() + 1))
        assertNull(dateFromEpochDay(Long.MAX_VALUE))
    }
}
