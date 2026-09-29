package io.github.bryancassell.bluecard.ui.badges

import android.icu.text.ListFormatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.data.progress.BadgeStatus
import java.util.Locale

/** Connects the Badges screen to its ViewModel. */
@Composable
fun BadgesRoute(
    onOpenBadge: (badgeId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BadgesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BadgesScreen(uiState = uiState, onOpenBadge = onOpenBadge, modifier = modifier)
}

/** Every merit badge, with whether it's Eagle-required and the scout's progress on it. */
@Composable
fun BadgesScreen(
    uiState: BadgesUiState,
    onOpenBadge: (badgeId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.badges_title),
            style = MaterialTheme.typography.headlineMedium,
            // Lets screen reader users jump to it.
            modifier = Modifier
                .padding(16.dp)
                .semantics { heading() }
        )
        when (uiState) {
            BadgesUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            // A lazy list composes only the rows on screen, so the full catalog scrolls
            // smoothly. Keys keep each row's state with its badge.
            is BadgesUiState.Ready -> {
                // Lists names in the language of the surrounding string, not the device's,
                // which the app may not have strings for.
                val language = stringResource(R.string.strings_language)
                val listFormatter = remember(language) {
                    ListFormatter.getInstance(Locale.forLanguageTag(language))
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.badges, key = { it.id }) { badge ->
                        BadgeRow(
                            badge = badge,
                            listFormatter = listFormatter,
                            onClick = { onOpenBadge(badge.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeRow(badge: BadgeListItem, listFormatter: ListFormatter, onClick: () -> Unit) {
    val eagle = when (val requirement = badge.eagle) {
        null -> null

        EagleRequirement.Required -> stringResource(R.string.badges_eagle_required)

        is EagleRequirement.OneOf -> stringResource(
            R.string.badges_eagle_required_one_of,
            listFormatter.format(requirement.badgeNames)
        )
    }
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
        modifier = Modifier.clickable(
            onClickLabel = stringResource(R.string.badges_open_badge),
            role = Role.Button,
            onClick = onClick
        )
    )
}
