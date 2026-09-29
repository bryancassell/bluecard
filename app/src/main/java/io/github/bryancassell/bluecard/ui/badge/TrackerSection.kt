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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.ui.typedText
import java.time.LocalDate

// A requirement's tracker, on its page and in its row.

/** "5 sessions", or "8 of 12 weeks". */
@Composable
fun trackerCountLabel(count: TrackerCount): String = if (count.rowCount == null) {
    stringResource(R.string.tracker_count, count.recorded, count.rows)
} else {
    stringResource(R.string.tracker_count_of, count.recorded, count.rowCount, count.rows)
}

/**
 * A requirement's tracker on its page: a heading that says how much is filled in, then its
 * rows, each of which opens to fill in or change. A log has a button to add a row.
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
        tracker.rows.forEach { row ->
            TrackerRowItem(tracker.rowTitle, row, onOpen = { onOpenRow(row) })
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

/** A tracker row: its title, such as "Week 3", and its values, if it has any. */
@Composable
private fun TrackerRowItem(rowTitle: String, row: TrackerRow, onOpen: () -> Unit) {
    val values = trackerValuesText(row.values)
    ListItem(
        headlineContent = {
            Text(stringResource(R.string.tracker_row_title, rowTitle, row.number))
        },
        supportingContent = values?.let {
            { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        },
        trailingContent = {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
        },
        // ListItem already reads as one item to screen readers, announced as a button that
        // edits the row.
        modifier = Modifier.clickable(
            onClickLabel = stringResource(R.string.tracker_edit_row),
            role = Role.Button,
            onClick = onOpen
        )
    )
}

/** A row's values on one line, such as "Sep 12, 2026 · Running · 30", or null if it has none. */
@Composable
private fun trackerValuesText(values: List<TrackerValue>): String? {
    val formatter = rememberCompletionDateFormatter()
    return values.takeIf { it.isNotEmpty() }?.map { value ->
        when (value.type) {
            TrackerColumnType.DATE -> formatter.format(LocalDate.parse(value.text))

            // The scout typed it.
            TrackerColumnType.NUMBER, TrackerColumnType.TEXT -> typedText(value.text)
        }
    }?.joinToString(" · ")
}
