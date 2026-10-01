package io.github.bryancassell.bluecard.data.progress

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoredDateTest {
    @Test
    fun storedDate_readsYearMonthDay() {
        assertEquals(LocalDate.of(2026, 9, 12), storedDate("2026-09-12"))
    }

    @Test
    fun storedDate_thatIsntADate_isNull() {
        assertNull(storedDate("Last Tuesday"))
        assertNull(storedDate("2026-02-30"))
    }
}
