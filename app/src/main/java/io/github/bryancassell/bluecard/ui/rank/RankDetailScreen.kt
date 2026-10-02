package io.github.bryancassell.bluecard.ui.rank

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import io.github.bryancassell.bluecard.ui.ScreenMessage
import io.github.bryancassell.bluecard.ui.TaskFailure
import io.github.bryancassell.bluecard.ui.TaskFailureSnackbar
import io.github.bryancassell.bluecard.ui.badge.AdvancementHeader
import io.github.bryancassell.bluecard.ui.badge.ClearProgress
import io.github.bryancassell.bluecard.ui.badge.EditableDate
import io.github.bryancassell.bluecard.ui.badge.LoadingIndicator
import io.github.bryancassell.bluecard.ui.badge.PickDateButton
import io.github.bryancassell.bluecard.ui.badge.RequirementRows
import io.github.bryancassell.bluecard.ui.badge.rememberCompletionDateFormatter
import io.github.bryancassell.bluecard.ui.rememberOtherAppStarter
import java.time.LocalDate

/** Connects the Rank detail screen to its ViewModel. */
@Composable
fun RankDetailRoute(
    rankId: String,
    onOpenRequirement: (number: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RankDetailViewModel =
        hiltViewModel<RankDetailViewModel, RankDetailViewModel.Factory> { it.create(rankId) }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RankDetailScreen(
        uiState = uiState,
        onOpenRequirement = onOpenRequirement,
        today = viewModel::today,
        onMarkEarned = viewModel::markEarned,
        onUnmarkEarned = viewModel::unmarkEarned,
        onClear = viewModel::clear,
        onSaveFailureShown = viewModel::onSaveFailureShown,
        modifier = modifier
    )
}

/**
 * A rank's summary, how much of it is done while it shows a bar, a link to its official
 * requirements, and its top-level requirements, each opening its own page, as on Badge detail.
 *
 * While the rank isn't earned, the scout can mark it earned on a date they pick, up to [today],
 * without recording its requirements, as for a rank earned before they used the app
 * ([onMarkEarned]). The date then shows, and they can change it or unmark the rank
 * ([onUnmarkEarned]). A rank that counts as earned only because a rank above it is marked says
 * so, and the scout can give it a date of its own the same way. At the bottom, once the rank is
 * started, the scout can clear its progress ([onClear]).
 */
@Composable
fun RankDetailScreen(
    uiState: RankDetailUiState,
    onOpenRequirement: (number: String) -> Unit,
    today: () -> LocalDate,
    onMarkEarned: (date: LocalDate) -> Unit,
    onUnmarkEarned: () -> Unit,
    onClear: () -> Unit,
    onSaveFailureShown: (TaskFailure) -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        RankDetailUiState.Loading -> LoadingIndicator(modifier)

        RankDetailUiState.LoadFailed -> LoadFailedMessage(modifier)

        RankDetailUiState.Unavailable ->
            ScreenMessage(stringResource(R.string.rank_requirements_unavailable), modifier)

        is RankDetailUiState.Ready -> Box(modifier = modifier) {
            RankDetails(uiState, onOpenRequirement, today, onMarkEarned, onUnmarkEarned, onClear)
            val snackbarHostState = remember { SnackbarHostState() }
            TaskFailureSnackbar(
                failure = uiState.saveFailure,
                message = stringResource(R.string.save_failed),
                onShown = onSaveFailureShown,
                hostState = snackbarHostState
            )
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun RankDetails(
    uiState: RankDetailUiState.Ready,
    onOpenRequirement: (number: String) -> Unit,
    today: () -> LocalDate,
    onMarkEarned: (date: LocalDate) -> Unit,
    onUnmarkEarned: () -> Unit,
    onClear: () -> Unit
) {
    // Opens the official requirements in the browser, once for quick taps.
    val startOtherApp = rememberOtherAppStarter()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AdvancementHeader(
            name = uiState.name,
            fractionDone = uiState.fractionDone,
            summary = uiState.summary,
            officialUrl = uiState.officialUrl,
            startOtherApp = startOtherApp,
            // Only the lowest rank not earned is in progress, as on Ranks.
            inProgress = uiState.status == RankStatus.InProgress
        )
        // Outside the header's column, so its text buttons line up with the page's text.
        EarnedOnPriorDate(uiState, today, onMarkEarned, onUnmarkEarned)
        Text(
            text = stringResource(R.string.badge_detail_requirements),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { heading() }
        )
        RequirementRows(items = uiState.requirements, onOpen = onOpenRequirement)
        if (uiState.canClear) {
            ClearProgress(
                title = stringResource(R.string.badge_detail_clear_title, uiState.name),
                message = stringResource(R.string.badge_detail_clear_message),
                onClear = onClear
            )
        }
    }
}

/**
 * While the rank isn't earned, a button to mark it earned on a date the scout picks, up to
 * [today], without recording its requirements ([onMark]). Once it's marked, the date, with
 * buttons to change it ([onMark]) or unmark the rank ([onUnmark]). For a rank that counts as
 * earned only with a rank above it, says so, with a button to give it a date of its own
 * ([onMark]). Each picker opens at the date the scout just unmarked, if any, or at today.
 * Nothing for a rank earned from its requirements.
 *
 * Its text buttons' touch areas are taller than they look, so they need no padding of their own
 * to keep it apart from what's above and below.
 */
@Composable
private fun EarnedOnPriorDate(
    uiState: RankDetailUiState.Ready,
    today: () -> LocalDate,
    onMark: (date: LocalDate) -> Unit,
    onUnmark: () -> Unit
) {
    val date = uiState.earnedOnPriorDate
    // Its text, unlike a button's, sits at the top of its space.
    val dateModifier = Modifier.padding(top = 16.dp)
    when {
        date != null -> EditableDate(
            text = stringResource(
                R.string.rank_detail_earned_on,
                rememberCompletionDateFormatter().format(date)
            ),
            date = date,
            today = today,
            onDateChange = { if (it == null) onUnmark() else onMark(it) },
            modifier = dateModifier,
            removeText = R.string.badge_detail_unmark_completed
        )

        uiState.earnedWith != null -> EditableDate(
            text = stringResource(R.string.rank_detail_earned_with, uiState.earnedWith),
            date = null,
            today = today,
            // Without a date, it can only be picked.
            onDateChange = { it?.let(onMark) },
            modifier = dateModifier,
            suggested = uiState.unmarkedDate
        )

        uiState.status != RankStatus.Earned -> PickDateButton(
            text = stringResource(R.string.rank_detail_mark_earned),
            // At the date the scout just unmarked, if any, so a mistaken Unmark loses nothing.
            initial = uiState.unmarkedDate,
            today = today,
            onPick = onMark,
            // Lines the button's text up with the page's.
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
