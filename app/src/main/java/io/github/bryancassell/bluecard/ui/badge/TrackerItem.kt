package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition
import io.github.bryancassell.bluecard.data.progress.TrackerEntry
import io.github.bryancassell.bluecard.data.progress.TrackerTotal
import io.github.bryancassell.bluecard.data.progress.columnValues
import io.github.bryancassell.bluecard.data.progress.filledRows
import io.github.bryancassell.bluecard.data.progress.numberedRows
import io.github.bryancassell.bluecard.data.progress.totals

/**
 * A requirement's tracker as its page shows it: how much is filled in, and its rows. A log,
 * a tracker without a fixed number of rows, lists its entries and has a button to add one. A
 * tracker with a fixed number of rows lists every row, filled in or not.
 */
data class TrackerItem(
    val count: TrackerCount,
    /** What one row is called, capitalized for titles such as "Week 3". */
    val rowTitle: String,
    /** What one row is called, in lowercase, such as "session" in "Add session". */
    val rowLabel: String,
    val rows: List<TrackerRow>
) {
    /** Whether the scout adds rows, as to a log. A fixed-row tracker has all of its rows. */
    val addsRows: Boolean get() = count.rowCount == null
}

/**
 * How much of a tracker is filled in, for its requirement: "5 sessions" in a log, or
 * "8 of 12 weeks" in a tracker with a fixed number of rows, or in a log that needs a number of
 * them, and its columns' totals, if it has any, such as "4 of 6 hours".
 */
data class TrackerCount(
    /** The entries recorded; in a tracker with a fixed number of rows, the rows filled in. */
    val recorded: Int,
    /** The fixed number of rows, or null for a log. */
    val rowCount: Int?,
    /** What the rows are called, agreeing with the number: "session" or "sessions". */
    val rows: String,
    /** Its columns that have a total ([totals]), in column order. */
    val totals: List<TrackerTotal> = emptyList(),
    /** In a log, how many rows its requirement asks for, if the catalog says. */
    val rowsNeeded: Int? = null
) {
    /** What [recorded] is out of, as in "8 of 12 weeks", or null to show it alone. */
    val outOf: Int? get() = rowCount ?: rowsNeeded
}

/** One row of a tracker. */
data class TrackerRow(
    /** The row it is, from 1: the one it fills in a fixed-row tracker, or its place in a log. */
    val number: Int,
    /** Its entry, or null for a row of a fixed-row tracker that isn't filled in. */
    val entryId: Long?,
    /** Its values in column order, without the columns it has none for. */
    val values: List<TrackerValue>
)

/** A value in a tracker row, as stored: a date is written as YYYY-MM-DD. */
data class TrackerValue(val type: TrackerColumnType, val text: String)

/** This tracker with the [entries] recorded for its requirement. */
fun TrackerDefinition.toItem(entries: List<TrackerEntry>) = TrackerItem(
    count = count(entries),
    rowTitle = rowTitle,
    rowLabel = rowLabel,
    rows = rows(entries)
)

/** How much of this tracker the [entries] recorded for its requirement fill in. */
fun TrackerDefinition.count(entries: List<TrackerEntry>): TrackerCount {
    val recorded = rowCount?.let { count -> filledRows(entries, count).size } ?: entries.size
    return TrackerCount(recorded, rowCount, rowsLabel(recorded), totals(entries), rowsNeeded)
}

private fun TrackerDefinition.rows(entries: List<TrackerEntry>): List<TrackerRow> =
    numberedRows(entries).map { (number, entry) ->
        val rowValues = entry?.let { columnValues(it) }.orEmpty()
            .map { (column, text) -> TrackerValue(column.type, text) }
        TrackerRow(number, entry?.id, rowValues)
    }
