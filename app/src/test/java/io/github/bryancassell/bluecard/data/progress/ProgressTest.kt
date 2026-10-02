package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressTest {
    private val columns = listOf(TrackerColumn("notes", "Notes", TrackerColumnType.TEXT))
    private val log = TrackerDefinition(columns, "session", "sessions")
    private val weeks = TrackerDefinition(columns, "week", "weeks", rowCount = 3)

    private fun entry(id: Long, rowNumber: Int? = null) = TrackerEntry(
        id = id,
        badgeId = "camping",
        requirementNumber = "1",
        rowNumber = rowNumber,
        values = mapOf("notes" to "Entry $id")
    )

    @Test
    fun storedDate_readsYearMonthDay() {
        assertEquals(LocalDate.of(2026, 9, 12), storedDate("2026-09-12"))
    }

    @Test
    fun storedDate_thatIsntADate_isNull() {
        assertNull(storedDate("Last Tuesday"))
        assertNull(storedDate("2026-02-30"))
    }

    @Test
    fun numberedRows_ofLog_areItsEntriesInTheOrderAdded() {
        val first = entry(3)
        val second = entry(8)

        assertEquals(listOf(1 to first, 2 to second), log.numberedRows(listOf(second, first)))
    }

    @Test
    fun numberedRows_withFixedRows_areEveryRow_withItsEntryOrNull() {
        val third = entry(1, rowNumber = 3)
        val first = entry(2, rowNumber = 1)

        assertEquals(
            listOf(1 to first, 2 to null, 3 to third),
            weeks.numberedRows(listOf(third, first))
        )
    }

    @Test
    fun values_areInColumnOrder_withoutColumnsTheEntryHasNoneFor() {
        val date = TrackerColumn("date", "Date", TrackerColumnType.DATE)
        val nights = TrackerColumn("nights", "Nights", TrackerColumnType.NUMBER)
        val place = TrackerColumn("place", "Place", TrackerColumnType.TEXT)
        val campouts = TrackerDefinition(listOf(date, nights, place), "campout", "campouts")
        // Stored in another order, without a date, and with a value for a column the tracker
        // doesn't have.
        val entry = entry(1).copy(
            values = mapOf("place" to "Bear Mountain", "removed" to "Old", "nights" to "2")
        )

        assertEquals(listOf(nights to "2", place to "Bear Mountain"), campouts.values(entry))
    }
}
