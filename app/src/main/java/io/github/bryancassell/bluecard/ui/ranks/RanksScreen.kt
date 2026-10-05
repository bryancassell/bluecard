package io.github.bryancassell.bluecard.ui.ranks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.data.progress.RankStatus
import io.github.bryancassell.bluecard.ui.LoadFailedMessage
import io.github.bryancassell.bluecard.ui.badge.LoadingIndicator
import io.github.bryancassell.bluecard.ui.badges.AdvancementRow

/** Connects the Ranks screen to its ViewModel. */
@Composable
fun RanksRoute(
    onOpenRank: (rankId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RanksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RanksScreen(uiState = uiState, onOpenRank = onOpenRank, modifier = modifier)
}

/**
 * Every rank, Scout through Eagle, in the order they're earned, with the scout's standing on
 * each: earned, the one in progress, or how much is done of one started out of order.
 */
@Composable
fun RanksScreen(
    uiState: RanksUiState,
    onOpenRank: (rankId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.ranks_title),
            style = MaterialTheme.typography.headlineMedium,
            // Lets screen reader users jump to it.
            modifier = Modifier
                .padding(16.dp)
                .semantics { heading() }
        )
        when (uiState) {
            RanksUiState.Loading -> LoadingIndicator()

            RanksUiState.LoadFailed -> LoadFailedMessage()

            // A lazy list, as on Badges, scrolls when large text makes the ranks taller than
            // the screen, and screen readers say how many it has.
            is RanksUiState.Ready -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(uiState.ranks, key = { it.id }) { rank ->
                    RankRow(rank, onClick = { onOpenRank(rank.id) })
                }
            }
        }
    }
}

/**
 * One rank in the list, in the same row as a badge's: its name and status, with a bar for how
 * much is done while it shows one.
 */
@Composable
fun RankRow(rank: RankListItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AdvancementRow(
        name = rank.name,
        detail = null,
        status = when (rank.status) {
            RankStatus.NotEarned -> null
            RankStatus.InProgress -> stringResource(R.string.badges_in_progress)
            RankStatus.Earned -> stringResource(R.string.ranks_earned)
        },
        fractionDone = rank.fractionDone,
        onClickLabel = stringResource(R.string.ranks_open_rank),
        onClick = onClick,
        modifier = modifier
    )
}
