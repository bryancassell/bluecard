package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.progress.TrackerTotal
import io.github.bryancassell.bluecard.data.progress.storedDate
import io.github.bryancassell.bluecard.text.lineBreaksAsSpaces
import io.github.bryancassell.bluecard.text.totalFormat
import io.github.bryancassell.bluecard.ui.readAsOneLabel
import io.github.bryancassell.bluecard.ui.stringsLocale
import io.github.bryancassell.bluecard.ui.typedText
import java.text.NumberFormat
import java.time.format.DateTimeFormatter

// A requirement's tracker, on its page and in its row.

/** "5 sessions", or, when it's out of a number, "8 of 12 weeks" or "6 of 10 animals". */
@Composable
fun trackerCountLabel(count: TrackerCount): String = when (val outOf = count.outOf) {
    null -> stringResource(R.string.tracker_count, count.recorded, count.rows)
    else -> stringResource(R.string.tracker_count_of, count.recorded, outOf, count.rows)
}

/** Formats a tracker's totals in the strings' language ([totalFormat]). */
@Composable
fun rememberTotalFormat(): NumberFormat {
    val locale = stringsLocale()
    return remember(locale) { totalFormat(locale) }
}

/** "4.5 of 6 hours", with the total written by [format] ([rememberTotalFormat]). */
@Composable
fun trackerTotalLabel(total: TrackerTotal, format: NumberFormat): String = stringResource(
    R.string.tracker_total,
    format.format(total.sum),
    total.total.needed,
    total.total.neededLabel
)

/**
 * A requirement's tracker on its page: a heading that says how much is filled in, its columns'
 * totals, if it has any, then its rows, each of which opens to fill in or change. A log has a
 * button to add a row.
 */
@Composable
fun TrackerSection(
    tracker: TrackerItem,
    onOpenRow: (TrackerRow) -> Unit,
    onAddRow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            text = trackerCountLabel(tracker.count),
            style = MaterialTheme.typography.titleMedium,
            // Lets screen reader users jump to it.
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { heading() }
        )
        if (tracker.count.totals.isNotEmpty()) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                val format = rememberTotalFormat()
                tracker.count.totals.forEach {
                    Text(
                        text = trackerTotalLabel(it, format),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        val formatter = rememberCompletionDateFormatter()
        tracker.rows.forEach { row ->
            TrackerRowItem(tracker.rowTitle, row, formatter, onOpen = { onOpenRow(row) })
        }
        if (tracker.addsRows) {
            OutlinedButton(onClick = onAddRow, modifier = Modifier.padding(horizontal = 16.dp)) {
                Icon(
                    painterResource(R.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.tracker_add_row, tracker.rowLabel))
            }
        }
    }
}

/**
 * A tracker row: its title, such as "Week 3", and its values, if it has any, with dates written
 * by [formatter].
 */
@Composable
private fun TrackerRowItem(
    rowTitle: String,
    row: TrackerRow,
    formatter: DateTimeFormatter,
    onOpen: () -> Unit
) {
    val title = stringResource(R.string.tracker_row_title, rowTitle, row.number)
    val values = trackerValuesText(row.values, formatter)
    // Its values in full, though only two lines of them show.
    val label = readAsOneLabel(title, values)
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = values?.let {
            { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        },
        trailingContent = {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
        },
        // Screen readers read the row as one button that edits it, with a label of its own:
        // TalkBack leaves out parts scrolled off screen (#312).
        modifier = Modifier
            .clickable(
                onClickLabel = stringResource(R.string.tracker_edit_row),
                role = Role.Button,
                onClick = onOpen
            )
            .clearAndSetSemantics { contentDescription = label }
    )
}

/**
 * A row's values on one line, such as "Sep 12, 2026 · Running · 30", or null if it has none. A
 * line break in a value, as in multi-line text, is a space.
 */
@Composable
private fun trackerValuesText(values: List<TrackerValue>, formatter: DateTimeFormatter): String? {
    val separator = stringResource(R.string.tracker_value_separator)
    return values.takeIf { it.isNotEmpty() }?.map { value ->
        when (value.type) {
            TrackerColumnType.DATE ->
                storedDate(value.text)?.let { formatter.format(it) } ?: typedText(value.text)

            // The scout typed it.
            TrackerColumnType.NUMBER, TrackerColumnType.TEXT, TrackerColumnType.MULTILINE_TEXT ->
                typedText(value.text)
        }
    }?.joinToString(separator)?.let(::lineBreaksAsSpaces)
}
