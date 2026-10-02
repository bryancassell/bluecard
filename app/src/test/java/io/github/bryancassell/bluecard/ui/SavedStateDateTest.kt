package io.github.bryancassell.bluecard.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedStateDateTest {
    @Test
    fun epochDay_isItsDate() {
        val day = LocalDate.of(2026, 4, 15)

        assertEquals(day, dateFromEpochDay(day.toEpochDay()))
    }

    @Test
    fun firstAndLastDaysLocalDateCanHold_areDates() {
        assertEquals(LocalDate.MIN, dateFromEpochDay(LocalDate.MIN.toEpochDay()))
        assertEquals(LocalDate.MAX, dateFromEpochDay(LocalDate.MAX.toEpochDay()))
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
        assertNull(dateFromEpochDay(LocalDate.MIN.toEpochDay() - 1))
        assertNull(dateFromEpochDay(LocalDate.MAX.toEpochDay() + 1))
        assertNull(dateFromEpochDay(Long.MAX_VALUE))
    }
}
