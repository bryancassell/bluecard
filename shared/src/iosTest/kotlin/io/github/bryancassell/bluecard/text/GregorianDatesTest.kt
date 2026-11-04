package io.github.bryancassell.bluecard.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970

class GregorianDatesTest {
    private val calendar = gregorianCalendar()

    @Test
    fun toNSDate_isTheStartOfTheDayInUtc() {
        // 20,593 days after Jan 1, 1970.
        val date = LocalDate(2026, 5, 20)

        assertEquals(20_593 * 86_400.0, date.toNSDate(calendar).timeIntervalSince1970)
    }

    @Test
    fun toLocalDate_isTheDayInUtc() {
        val lastSecond = NSDate.dateWithTimeIntervalSince1970(20_594 * 86_400.0 - 1)

        assertEquals(LocalDate(2026, 5, 20), lastSecond.toLocalDate(calendar))
    }

    @Test
    fun toLocalDate_afterToNSDate_isTheSameDay() {
        val dates = listOf(LocalDate(1900, 1, 1), LocalDate(2024, 2, 29), LocalDate(2026, 12, 31))

        for (date in dates) {
            assertEquals(date, date.toNSDate(calendar).toLocalDate(calendar))
        }
    }
}
