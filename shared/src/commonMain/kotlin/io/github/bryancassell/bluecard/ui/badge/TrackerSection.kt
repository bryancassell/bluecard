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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.progress.TrackerTotal
import io.github.bryancassell.bluecard.data.progress.storedDate
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.ic_add
import io.github.bryancassell.bluecard.resources.ic_chevron_right
import io.github.bryancassell.bluecard.resources.tracker_add_row
import io.github.bryancassell.bluecard.resources.tracker_count
import io.github.bryancassell.bluecard.resources.tracker_count_of
import io.github.bryancassell.bluecard.resources.tracker_edit_row
import io.github.bryancassell.bluecard.resources.tracker_row_title
import io.github.bryancassell.bluecard.resources.tracker_total
import io.github.bryancassell.bluecard.resources.tracker_value_separator
import io.github.bryancassell.bluecard.text.lineBreaksAsSpaces
import io.github.bryancassell.bluecard.ui.LocalLocaleFormats
import io.github.bryancassell.bluecard.ui.readAsOneLabel
import io.github.bryancassell.bluecard.ui.typedText
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// A requirement's tracker, on its page and in its row.

/**
 * "5 sessions", or, when it's out of a number, "8 of 12 weeks" or "6 of 10 animals". A log's
 * count goes on past the number, "12 of 10 animals", as totals and merit badge counts do, so it
 * shows every row the scout recorded.
 */
@Composable
fun trackerCountLabel(count: TrackerCount): String = when (val outOf = count.outOf) {
    null -> stringResource(Res.string.tracker_count, count.recorded, count.rows)
    else -> stringResource(Res.string.tracker_count_of, count.recorded, outOf, count.rows)
}

/** "4.5 of 6 hours", with the total in the strings' language ([LocalLocaleFormats]). */
@Composable
fun trackerTotalLabel(total: TrackerTotal): String = stringResource(
    Res.string.tracker_total,
    LocalLocaleFormats.current.formatTotal(total.sum),
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
                tracker.count.totals.forEach {
                    Text(
                        text = trackerTotalLabel(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        tracker.rows.forEach { row ->
            TrackerRowItem(tracker.rowTitle, row, onOpen = { onOpenRow(row) })
        }
        if (tracker.addsRows) {
            OutlinedButton(onClick = onAddRow, modifier = Modifier.padding(horizontal = 16.dp)) {
                Icon(
                    painterResource(Res.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(Res.string.tracker_add_row, tracker.rowLabel))
            }
        }
    }
}

/** A tracker row: its title, such as "Week 3", and its values, if it has any. */
@Composable
private fun TrackerRowItem(rowTitle: String, row: TrackerRow, onOpen: () -> Unit) {
    val title = stringResource(Res.string.tracker_row_title, rowTitle, row.number)
    val values = trackerValuesText(row.values)
    // Its values in full, though only two lines of them show.
    val label = readAsOneLabel(title, values)
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = values?.let {
            { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        },
        trailingContent = {
            Icon(painterResource(Res.drawable.ic_chevron_right), contentDescription = null)
        },
        // Screen readers read the row as one button that edits it, with a label of its own:
        // TalkBack leaves out parts scrolled off screen (#312).
        modifier = Modifier
            .clickable(
                onClickLabel = stringResource(Res.string.tracker_edit_row),
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
private fun trackerValuesText(values: List<TrackerValue>): String? {
    val separator = stringResource(Res.string.tracker_value_separator)
    val formats = LocalLocaleFormats.current
    return values.takeIf { it.isNotEmpty() }?.map { value ->
        when (value.type) {
            TrackerColumnType.DATE ->
                storedDate(value.text)?.let(formats::formatDate) ?: typedText(value.text)

            // The scout typed it.
            TrackerColumnType.NUMBER, TrackerColumnType.TEXT, TrackerColumnType.MULTILINE_TEXT ->
                typedText(value.text)
        }
    }?.joinToString(separator)?.let(::lineBreaksAsSpaces)
}
