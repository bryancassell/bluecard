package io.github.bryancassell.bluecard.ui.badges

import android.icu.text.ListFormatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.data.progress.BadgeStatus
import io.github.bryancassell.bluecard.ui.statusFitsBeside

/**
 * One badge in a list, on Badges and on Home: its name, whether it's Eagle-required and the
 * scout's progress on it, with a bar for how much is done while it's in progress. Tapping it
 * calls [onClick], which opens the badge.
 */
@Composable
fun BadgeRow(
    badge: BadgeListItem,
    listFormatter: ListFormatter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AdvancementRow(
        name = badge.name,
        detail = badge.eagle?.let { eagleRequirementLabel(it, listFormatter) },
        status = when (badge.status) {
            BadgeStatus.NotStarted -> null
            BadgeStatus.InProgress -> stringResource(R.string.badges_in_progress)
            BadgeStatus.Completed -> stringResource(R.string.badges_completed)
        },
        fractionDone = badge.fractionDone,
        onClickLabel = stringResource(R.string.badges_open_badge),
        onClick = onClick,
        modifier = modifier
    )
}

/**
 * One badge or rank in a list: its [name], a line of [detail] under it, if any, the scout's
 * [status] on it at the row's end, if any, and a bar for how much is done, [fractionDone] from 0
 * to 1, if it shows one. Where the status would leave too little room beside it for a word of the
 * name or detail, as at the largest text and display size, it goes under them, above the bar
 * (PRD.md's Status in a narrow row). Tapping it calls [onClick], which screen readers announce
 * with [onClickLabel].
 */
@Composable
fun AdvancementRow(
    name: String,
    detail: String?,
    status: String?,
    fractionDone: Float?,
    onClickLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val percentDone = fractionDone?.let { percentDoneDescription(it) }
    BoxWithConstraints(modifier) {
        val typography = MaterialTheme.typography
        // In the styles ListItem gives its slots, and the row's width less the 16dp ListItem puts
        // at each end and before the status.
        val statusUnder = status?.takeUnless {
            statusFitsBeside(
                status = it,
                statusStyle = typography.labelSmall,
                lines = listOfNotNull(
                    name to typography.bodyLarge,
                    detail?.let { detail -> detail to typography.bodyMedium }
                ),
                width = maxWidth - 16.dp * 3
            )
        }
        ListItem(
            headlineContent = { Text(name) },
            supportingContent = if (detail == null && statusUnder == null && fractionDone == null) {
                null
            } else {
                {
                    Column {
                        detail?.let { Text(it) }
                        // Read after the detail, as it is beside it.
                        statusUnder?.let { Text(it, style = typography.labelSmall) }
                        fractionDone?.let {
                            BadgeProgressBar(
                                fractionDone = it,
                                // The row says how much is done, as its state.
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .semantics { hideFromAccessibility() }
                            )
                        }
                    }
                }
            },
            trailingContent = status.takeIf { statusUnder == null }?.let { { Text(it) } },
            // Screen readers announce the row as a button that opens the badge or rank. Compose
            // reports a progress bar's percentage only from the bar's own node, not from the row
            // it's merged into, so the row says how much is done as its state.
            modifier = Modifier
                .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
                .semantics { percentDone?.let { stateDescription = it } }
        )
    }
}
