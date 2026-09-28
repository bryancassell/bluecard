package io.github.bryancassell.bluecard.ui.badges

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.R

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
            modifier = Modifier.padding(16.dp)
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
            is BadgesUiState.Ready -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(uiState.badges, key = { it.id }) { badge ->
                    BadgeRow(badge = badge, onClick = { onOpenBadge(badge.id) })
                }
            }
        }
    }
}

@Composable
private fun BadgeRow(badge: BadgeListItem, onClick: () -> Unit) {
    val status = when (badge.status) {
        BadgeStatus.NotStarted -> null
        BadgeStatus.InProgress -> R.string.badges_in_progress
        BadgeStatus.Completed -> R.string.badges_completed
    }
    ListItem(
        headlineContent = { Text(badge.name) },
        supportingContent = if (badge.eagleRequired) {
            { Text(stringResource(R.string.badges_eagle_required)) }
        } else {
            null
        },
        trailingContent = status?.let { { Text(stringResource(it)) } },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
