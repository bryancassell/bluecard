package io.github.bryancassell.bluecard.ui.badges

import android.icu.text.ListFormatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
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
    val eagle = badge.eagle?.let { eagleRequirementLabel(it, listFormatter) }
    val status = when (badge.status) {
        BadgeStatus.NotStarted -> null
        BadgeStatus.InProgress -> stringResource(R.string.badges_in_progress)
        BadgeStatus.Completed -> stringResource(R.string.badges_completed)
    }
    val fractionDone = badge.fractionDone
    val percentDone = fractionDone?.let { percentDoneDescription(it) }
    ListItem(
        headlineContent = { Text(badge.name) },
        supportingContent = if (eagle == null && fractionDone == null) {
            null
        } else {
            {
                Column {
                    eagle?.let { Text(it) }
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
        trailingContent = status?.let { { Text(it) } },
        // Screen readers announce the row as a button that opens the badge. Compose reports a
        // progress bar's percentage only from the bar's own node, not from the row it's merged
        // into, so the row says how much is done as its state.
        modifier = modifier
            .clickable(
                onClickLabel = stringResource(R.string.badges_open_badge),
                role = Role.Button,
                onClick = onClick
            )
            .semantics { percentDone?.let { stateDescription = it } }
    )
}
