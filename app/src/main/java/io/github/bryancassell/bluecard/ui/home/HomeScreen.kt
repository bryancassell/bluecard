package io.github.bryancassell.bluecard.ui.home

import androidx.annotation.PluralsRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.ui.LoadFailedMessage
import io.github.bryancassell.bluecard.ui.badges.BadgeListItem
import io.github.bryancassell.bluecard.ui.badges.BadgeRow
import io.github.bryancassell.bluecard.ui.badges.rememberBadgeNameListFormatter
import io.github.bryancassell.bluecard.ui.typedText

/** Connects the Home screen to its ViewModel. */
@Composable
fun HomeRoute(
    onOpenBadge: (badgeId: String) -> Unit,
    onOpenBadges: () -> Unit,
    onOpenRanks: () -> Unit,
    onOpenRank: (rankId: String) -> Unit,
    onOpenDataManagement: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onOpenBadge = onOpenBadge,
        onOpenBadges = onOpenBadges,
        onOpenRanks = onOpenRanks,
        onOpenRank = onOpenRank,
        onOpenDataManagement = onOpenDataManagement,
        modifier = modifier
    )
}

/**
 * The scout's name and unit, a card with their rank and the rank they're working toward, a summary
 * of their merit badge progress, and the badges they have in progress, with buttons that open
 * Badges, Ranks and Data management.
 */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onOpenBadge: (badgeId: String) -> Unit,
    onOpenBadges: () -> Unit,
    onOpenRanks: () -> Unit,
    onOpenRank: (rankId: String) -> Unit,
    onOpenDataManagement: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        HomeUiState.Loading -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }

        HomeUiState.LoadFailed -> LoadFailedMessage(modifier)

        is HomeUiState.Ready -> Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // The badge rows run edge to edge, as on Badges, so everything else is inset.
            val inset = Modifier.padding(horizontal = 16.dp)
            Column(modifier = inset) {
                Text(
                    text = typedText(uiState.name),
                    style = MaterialTheme.typography.headlineMedium,
                    // Lets screen reader users jump to it.
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = stringResource(R.string.home_unit, typedText(uiState.unitNumber)),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            RankCard(uiState, onOpenRank, modifier = inset)
            if (uiState.hasNoProgress) {
                Text(stringResource(R.string.home_no_progress), modifier = inset)
            } else {
                Summary(uiState, modifier = inset)
            }
            if (uiState.badgesInProgress.isNotEmpty()) {
                BadgesInProgress(uiState.badgesInProgress, onOpenBadge)
            }
            Button(onClick = onOpenBadges, modifier = inset.fillMaxWidth()) {
                Text(stringResource(R.string.home_open_badges))
            }
            Button(onClick = onOpenRanks, modifier = inset.fillMaxWidth()) {
                Text(stringResource(R.string.home_open_ranks))
            }
            OutlinedButton(onClick = onOpenDataManagement, modifier = inset.fillMaxWidth()) {
                Text(stringResource(R.string.home_open_data_management))
            }
        }
    }
}

@Composable
private fun Summary(uiState: HomeUiState.Ready, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SummaryCard(title = stringResource(R.string.home_badges_title)) {
            Text(countText(R.plurals.home_completed, uiState.badges.completed))
            Text(countText(R.plurals.home_in_progress, uiState.badges.inProgress))
        }
        // A catalog with no Eagle-required badges has no Eagle progress to show.
        if (uiState.eagleTotal > 0) {
            EagleCard(uiState)
        }
    }
}

/** The badges in progress, in the same rows as on Badges. */
@Composable
private fun BadgesInProgress(badges: List<BadgeListItem>, onOpenBadge: (badgeId: String) -> Unit) {
    val listFormatter = rememberBadgeNameListFormatter()
    // Screen readers say when focus enters and leaves the list and how many badges it has,
    // as on Badges.
    Column(
        modifier = Modifier.semantics {
            collectionInfo = CollectionInfo(rowCount = badges.size, columnCount = 1)
        }
    ) {
        badges.forEach { badge ->
            // Keeps each row's state with its badge as badges come and go.
            key(badge.id) {
                BadgeRow(
                    badge = badge,
                    listFormatter = listFormatter,
                    onClick = { onOpenBadge(badge.id) }
                )
            }
        }
    }
}

@Composable
private fun EagleCard(uiState: HomeUiState.Ready) {
    SummaryCard(title = stringResource(R.string.home_eagle_title)) {
        Text(
            pluralStringResource(
                R.plurals.home_eagle_completed,
                uiState.eagle.completed,
                uiState.eagle.completed,
                uiState.eagleTotal
            )
        )
        LinearProgressIndicator(
            progress = { uiState.eagle.completed.toFloat() / uiState.eagleTotal },
            // Material's default track color is almost the card's, so the bar's full length
            // wouldn't show.
            trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            // The text above says the same, so screen readers skip the bar rather than
            // read a percentage out of context.
            modifier = Modifier
                .fillMaxWidth()
                .semantics { hideFromAccessibility() }
        )
        Text(countText(R.plurals.home_in_progress, uiState.eagle.inProgress))
    }
}

@Composable
private fun SummaryCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            content()
        }
    }
}

/** A plural string whose only argument is its quantity, such as "3 in progress". */
@Composable
private fun countText(@PluralsRes id: Int, count: Int) = pluralStringResource(id, count, count)
