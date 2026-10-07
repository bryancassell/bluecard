package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.catalog.ColumnTotal
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackerTotalsTest {
    private val hours = ColumnTotal(6, "hour", "hours")
    private val conservationHours = ColumnTotal(3, "conservation hour", "conservation hours")
    private val log = TrackerDefinition(
        columns = listOf(
            TrackerColumn("date", "Date", TrackerColumnType.DATE),
            TrackerColumn("hours", "Hours", TrackerColumnType.NUMBER, hours),
            TrackerColumn(
                "conservation",
                "Conservation hours",
                TrackerColumnType.NUMBER,
                conservationHours
            ),
            TrackerColumn("miles", "Miles", TrackerColumnType.NUMBER)
        ),
        rowLabel = "project",
        rowLabelPlural = "projects"
    )

    private fun entry(id: Long, values: Map<String, String>, rowNumber: Int? = null) =
        TrackerEntry(id, "life", "4", rowNumber, values)

    @Test
    fun storedNumber_readsWholeAndDecimalNumbers() {
        assertEquals(BigDecimal("12"), storedNumber("12"))
        assertEquals(BigDecimal("1.5"), storedNumber("1.5"))
        assertEquals(BigDecimal("0.5"), storedNumber(".5"))
        assertEquals(BigDecimal("2"), storedNumber("2."))
    }

    // As the number field takes them (NumberInput).
    @Test
    fun storedNumber_readsAnySeparatorAndScriptTheScoutCanType() {
        assertEquals(BigDecimal("1.5"), storedNumber("1,5"))
        assertEquals(BigDecimal("12.5"), storedNumber("۱۲٫۵"))
        assertEquals(BigDecimal("30"), storedNumber("٣٠"))
    }

    // Import rejects these, but one stored while a catalog edited during development had the
    // column as text can be any text.
    @Test
    fun storedNumber_isNullForTextThatIsntANumber() {
        assertNull(storedNumber(""))
        assertNull(storedNumber("."))
        assertNull(storedNumber("1.2.3"))
        assertNull(storedNumber("1,000.5"))
        assertNull(storedNumber("-3"))
        assertNull(storedNumber("2 hours"))
    }

    @Test
    fun totals_addUpEachColumnWithATotal_inColumnOrder() {
        val entries = listOf(
            entry(1, mapOf("hours" to "2", "conservation" to "1.5", "miles" to "4")),
            entry(2, mapOf("hours" to "2,5")),
            entry(3, mapOf("hours" to "1", "conservation" to "1"))
        )

        val totals = log.totals(entries)

        assertEquals(listOf(hours, conservationHours), totals.map { it.total })
        assertEquals(BigDecimal("5.5"), totals[0].sum)
        assertEquals(BigDecimal("2.5"), totals[1].sum)
    }

    @Test
    fun totals_withNoEntries_areZero() {
        assertEquals(
            listOf(
                TrackerTotal(BigDecimal.ZERO, hours),
                TrackerTotal(BigDecimal.ZERO, conservationHours)
            ),
            log.totals(emptyList())
        )
    }

    @Test
    fun totals_leaveOutValuesThatArentNumbers() {
        val entries = listOf(entry(1, mapOf("hours" to "2")), entry(2, mapOf("hours" to "two")))

        assertEquals(BigDecimal("2"), log.totals(entries).first().sum)
    }

    @Test
    fun totals_withoutAColumnWithATotal_areEmpty() {
        val columns = log.columns.map { it.copy(total = null) }

        assertEquals(emptyList<TrackerTotal>(), log.copy(columns = columns).totals(emptyList()))
    }

    @Test
    fun neededLabel_agreesWithTheAmountNeeded() {
        assertEquals("hours", hours.neededLabel)
        assertEquals("hour", hours.copy(needed = 1).neededLabel)
    }
}
