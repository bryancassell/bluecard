package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition
import io.github.bryancassell.bluecard.data.progress.TrackerEntry
import java.time.LocalDate
import java.time.format.DateTimeParseException

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
 * "8 of 12 weeks" in a tracker with a fixed number of rows.
 */
data class TrackerCount(
    /** The entries recorded; in a tracker with a fixed number of rows, the rows filled in. */
    val recorded: Int,
    /** The fixed number of rows, or null for a log. */
    val rowCount: Int?,
    /** What the rows are called, agreeing with the number: "session" or "sessions". */
    val rows: String
)

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

/**
 * A date column's stored value as a date, or null if it isn't one. The app stores dates as
 * YYYY-MM-DD, but a value from elsewhere may not be, such as one stored while a catalog edited
 * during development had the column as text. It's shown as it is instead.
 */
fun storedDate(text: String): LocalDate? = try {
    LocalDate.parse(text)
} catch (e: DateTimeParseException) {
    null
}

/** This tracker with the [entries] recorded for its requirement. */
fun TrackerDefinition.toItem(entries: List<TrackerEntry>) = TrackerItem(
    count = count(entries),
    rowTitle = rowTitle,
    rowLabel = rowLabel,
    rows = rows(entries)
)

/** What one row is called, capitalized for titles such as "Week 3". */
val TrackerDefinition.rowTitle: String get() = rowLabel.replaceFirstChar { it.titlecase() }

/** How much of this tracker the [entries] recorded for its requirement fill in. */
fun TrackerDefinition.count(entries: List<TrackerEntry>): TrackerCount {
    val recorded = rowCount?.let { count -> filledRows(entries, count).size } ?: entries.size
    // The catalog is in English, so its row labels follow English plurals.
    val rows = if ((rowCount ?: recorded) == 1) rowLabel else rowLabelPlural
    return TrackerCount(recorded, rowCount, rows)
}

private fun TrackerDefinition.rows(entries: List<TrackerEntry>): List<TrackerRow> =
    if (rowCount == null) {
        // In the order they were added.
        entries.sortedBy { it.id }.mapIndexed { index, entry ->
            TrackerRow(index + 1, entry.id, values(entry))
        }
    } else {
        val filled = filledRows(entries, rowCount)
        (1..rowCount).map { number ->
            val entry = filled[number]
            TrackerRow(number, entry?.id, entry?.let { values(it) }.orEmpty())
        }
    }

/**
 * The entries of a tracker with [rowCount] rows, by the row they fill. Only a catalog edited
 * during development could leave an entry outside them.
 */
private fun filledRows(entries: List<TrackerEntry>, rowCount: Int): Map<Int, TrackerEntry> =
    entries.filter { it.rowNumber in 1..rowCount }.associateBy { it.rowNumber!! }

private fun TrackerDefinition.values(entry: TrackerEntry): List<TrackerValue> =
    columns.mapNotNull { column -> entry.values[column.id]?.let { TrackerValue(column.type, it) } }
