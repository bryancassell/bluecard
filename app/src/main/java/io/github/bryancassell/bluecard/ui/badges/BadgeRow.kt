package io.github.bryancassell.bluecard.ui.badges

import android.icu.text.ListFormatter
import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.data.progress.BadgeStatus

/**
 * One badge in a list, on Badges and on Home: its name, whether it's Eagle-required and the
 * scout's progress on it. Tapping it calls [onClick], which opens the badge.
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
    ListItem(
        headlineContent = { Text(badge.name) },
        supportingContent = eagle?.let { { Text(it) } },
        trailingContent = status?.let { { Text(it) } },
        // Screen readers announce the row as a button that opens the badge.
        modifier = modifier.clickable(
            onClickLabel = stringResource(R.string.badges_open_badge),
            role = Role.Button,
            onClick = onClick
        )
    )
}
