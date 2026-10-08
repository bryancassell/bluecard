package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.ColumnTotal
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition
import io.github.bryancassell.bluecard.data.progress.TrackerEntry
import io.github.bryancassell.bluecard.data.progress.TrackerTotal
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerItemTest {
    private val columns = listOf(
        TrackerColumn("date", "Date", TrackerColumnType.DATE),
        TrackerColumn("activity", "Activity", TrackerColumnType.TEXT),
        TrackerColumn("minutes", "Minutes", TrackerColumnType.NUMBER)
    )
    private val log = TrackerDefinition(columns, "session", "sessions")
    private val weeks = TrackerDefinition(columns, "week", "weeks", rowCount = 3)

    private fun entry(id: Long, values: Map<String, String>, rowNumber: Int? = null) =
        TrackerEntry(id, "personal-fitness", "7a", rowNumber, values)

    @Test
    fun log_countsItsEntries() {
        val entries = listOf(entry(1, mapOf("minutes" to "30")), entry(2, mapOf("minutes" to "45")))

        assertEquals(TrackerCount(2, null, "sessions"), log.count(entries))
    }

    @Test
    fun count_hasTheTotalsOfColumnsWithOne() {
        val hours = ColumnTotal(6, "hour", "hours")
        val withTotal = log.copy(
            columns = columns.map { if (it.id == "minutes") it.copy(total = hours) else it }
        )
        val entries = listOf(entry(1, mapOf("minutes" to "2")), entry(2, mapOf("minutes" to "3")))

        assertEquals(
            TrackerCount(2, null, "sessions", listOf(TrackerTotal(BigDecimal("5"), hours))),
            withTotal.count(entries)
        )
    }

    @Test
    fun log_withOneEntry_isCountedInTheSingular() {
        assertEquals(TrackerCount(1, null, "session"), log.count(listOf(entry(1, emptyMap()))))
    }

    @Test
    fun log_withNoEntries_isCountedInThePlural() {
        assertEquals(TrackerCount(0, null, "sessions"), log.count(emptyList()))
    }

    @Test
    fun logThatNeedsRows_isCountedOutOfThem_andStillAddsRows() {
        val atLeastThree = log.copy(rowsNeeded = 3)
        val fourEntries = (1L..4L).map { entry(it, emptyMap()) }

        val item = atLeastThree.toItem(fourEntries)

        assertEquals(TrackerCount(4, null, "sessions", outOf = 3), item.count)
        assertEquals(3, item.count.outOf)
        assertTrue(item.addsRows)
    }

    @Test
    fun outOf_isTheFixedRowsOrTheRowsNeeded() {
        assertEquals(3, weeks.count(emptyList()).outOf)
        assertEquals(null, log.count(emptyList()).outOf)
    }

    @Test
    fun fixedRows_countsRowsFilledIn_agreeingWithTheRowCount() {
        val entries = listOf(entry(1, emptyMap(), rowNumber = 1))

        assertEquals(TrackerCount(1, 3, "weeks"), weeks.count(entries))
        assertEquals(
            TrackerCount(1, 1, "week"),
            weeks.copy(rowCount = 1).count(entries)
        )
    }

    @Test
    fun fixedRows_ignoresEntriesOutsideItsRows() {
        // Only a catalog edited during development could leave these.
        val entries = listOf(
            entry(1, emptyMap(), rowNumber = 2),
            entry(2, emptyMap(), rowNumber = 4),
            entry(3, emptyMap(), rowNumber = null)
        )

        assertEquals(TrackerCount(1, 3, "weeks"), weeks.count(entries))
        assertEquals(listOf(null, 1L, null), weeks.toItem(entries).rows.map { it.entryId })
    }

    @Test
    fun log_listsEntriesInTheOrderAdded_numberedFromOne() {
        val entries = listOf(
            entry(7, mapOf("activity" to "Swim")),
            entry(3, mapOf("activity" to "Run"))
        )

        assertEquals(
            listOf(
                TrackerRow(1, 3, listOf(TrackerValue(TrackerColumnType.TEXT, "Run"))),
                TrackerRow(2, 7, listOf(TrackerValue(TrackerColumnType.TEXT, "Swim")))
            ),
            log.toItem(entries).rows
        )
    }

    @Test
    fun fixedRows_listsEveryRow_filledInOrNot() {
        val entries = listOf(entry(5, mapOf("minutes" to "20"), rowNumber = 2))

        assertEquals(
            listOf(
                TrackerRow(1, null, emptyList()),
                TrackerRow(2, 5, listOf(TrackerValue(TrackerColumnType.NUMBER, "20"))),
                TrackerRow(3, null, emptyList())
            ),
            weeks.toItem(entries).rows
        )
    }

    @Test
    fun rowValues_areInColumnOrder_withoutMissingOnes() {
        val entries = listOf(
            entry(1, mapOf("minutes" to "30", "date" to "2026-09-12", "unknown" to "x"))
        )

        assertEquals(
            listOf(
                TrackerValue(TrackerColumnType.DATE, "2026-09-12"),
                TrackerValue(TrackerColumnType.NUMBER, "30")
            ),
            log.toItem(entries).rows.single().values
        )
    }

    @Test
    fun rowLabel_isCapitalizedForTitles() {
        val item = weeks.toItem(emptyList())

        assertEquals("Week", item.rowTitle)
        assertEquals("week", item.rowLabel)
    }

    @Test
    fun onlyLogsAddRows() {
        assertTrue(log.toItem(emptyList()).addsRows)
        assertFalse(weeks.toItem(emptyList()).addsRows)
    }
}
