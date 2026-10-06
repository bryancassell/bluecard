package io.github.bryancassell.bluecard.ui.rank

import android.net.Uri
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.data.progress.RankStatus
import io.github.bryancassell.bluecard.data.report.ReportKind
import io.github.bryancassell.bluecard.data.report.reportFileName
import io.github.bryancassell.bluecard.ui.LoadingOrMessage
import io.github.bryancassell.bluecard.ui.TaskFailure
import io.github.bryancassell.bluecard.ui.TaskFailureSnackbar
import io.github.bryancassell.bluecard.ui.badge.AdvancementHeader
import io.github.bryancassell.bluecard.ui.badge.ClearProgress
import io.github.bryancassell.bluecard.ui.badge.EditableDate
import io.github.bryancassell.bluecard.ui.badge.PickDateButton
import io.github.bryancassell.bluecard.ui.badge.ReportButtons
import io.github.bryancassell.bluecard.ui.badge.RequirementRows
import io.github.bryancassell.bluecard.ui.badge.ShareReport
import io.github.bryancassell.bluecard.ui.badge.rememberCompletionDateFormatter
import io.github.bryancassell.bluecard.ui.badges.rememberBadgeNameListFormatter
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
        onShareReport = viewModel::shareReport,
        onReportShared = viewModel::onReportShared,
        onSaveReport = viewModel::saveReport,
        onReportFailureShown = viewModel::onReportFailureShown,
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
 * so, and the scout can give it a date of its own the same way.
 *
 * Once the rank is earned, its report can be shared, which asks for it to be created
 * ([onShareReport]) and opens the share sheet once it's ready ([onReportShared]), or saved,
 * which asks the scout where with the system file picker ([onSaveReport]). At the bottom, once
 * the rank is started, the scout can clear its progress ([onClear]).
 */
@Composable
fun RankDetailScreen(
    uiState: RankDetailUiState,
    onOpenRequirement: (number: String) -> Unit,
    today: () -> LocalDate,
    onMarkEarned: (date: LocalDate) -> Unit,
    onUnmarkEarned: () -> Unit,
    onShareReport: () -> Unit,
    onReportShared: () -> Unit,
    onSaveReport: (destination: Uri) -> Unit,
    onReportFailureShown: (TaskFailure) -> Unit,
    onClear: () -> Unit,
    onSaveFailureShown: (TaskFailure) -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        // One branch, so screen readers hear the message (see LoadingOrMessage).
        RankDetailUiState.Loading,
        RankDetailUiState.LoadFailed,
        RankDetailUiState.Unavailable -> LoadingOrMessage(
            message = when (uiState) {
                RankDetailUiState.LoadFailed -> stringResource(R.string.load_failed)

                RankDetailUiState.Unavailable ->
                    stringResource(R.string.rank_requirements_unavailable)

                else -> null
            },
            modifier = modifier
        )

        is RankDetailUiState.Ready -> Box(modifier = modifier) {
            RankDetails(
                uiState,
                onOpenRequirement,
                today,
                onMarkEarned,
                onUnmarkEarned,
                onShareReport,
                onReportShared,
                onSaveReport,
                onClear
            )
            // One host for both kinds of failure, so they show one at a time.
            val snackbarHostState = remember { SnackbarHostState() }
            TaskFailureSnackbar(
                failure = uiState.reportFailure,
                message = stringResource(R.string.report_failed),
                onShown = onReportFailureShown,
                hostState = snackbarHostState
            )
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
    onShareReport: () -> Unit,
    onReportShared: () -> Unit,
    onSaveReport: (destination: Uri) -> Unit,
    onClear: () -> Unit
) {
    // Opens the official requirements in the browser. The report shares it, so quick taps on
    // either open one app, once.
    val startOtherApp = rememberOtherAppStarter()
    uiState.reportToShare?.let { ShareReport(it, onReportShared) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AdvancementHeader(
            name = uiState.name,
            fractionDone = uiState.fractionDone,
            summary = uiState.summary,
            officialUrl = uiState.officialUrl,
            // Only the lowest rank not earned is in progress, as on Ranks.
            inProgress = uiState.status == RankStatus.InProgress,
            startOtherApp = startOtherApp
        )
        // Outside the header's column, so its text buttons line up with the page's text.
        EarnedStatus(uiState, today, onMarkEarned, onUnmarkEarned)
        if (uiState.status == RankStatus.Earned) {
            ReportButtons(
                reportFileName(LocalResources.current, ReportKind.Rank, uiState.name),
                onShareReport,
                onSaveReport,
                startOtherApp,
                // None above: what's above leaves room under it, as Change date and Add date's
                // touch areas do, or as "Earned on" does to match them.
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            )
        }
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
                message = if (uiState.unearnedByClear.isEmpty()) {
                    stringResource(R.string.badge_detail_clear_message)
                } else {
                    stringResource(
                        R.string.rank_detail_clear_message_unearns,
                        rememberBadgeNameListFormatter().format(uiState.unearnedByClear)
                    )
                },
                onClear = onClear
            )
        }
    }
}

/**
 * How the rank is earned, under the official link:
 * - While it isn't earned, a button to mark it earned on a date the scout picks, up to [today],
 *   without recording its requirements ([onMark]), under the rank it's waiting on, if its
 *   requirements are complete.
 * - Once it's marked, the date, with buttons to change it ([onMark]) or unmark the rank
 *   ([onUnmark]).
 * - For a rank that counts as earned only with a rank above it, says so, with a button to give
 *   it a date of its own ([onMark]).
 * - For a rank earned from its requirements, the date they were completed on.
 *
 * Each picker opens at the date the scout just unmarked, if any, or at today. Its text buttons'
 * touch areas are taller than they look, so they need no padding of their own to keep it apart
 * from what's above and below.
 */
@Composable
private fun EarnedStatus(
    uiState: RankDetailUiState.Ready,
    today: () -> LocalDate,
    onMark: (date: LocalDate) -> Unit,
    onUnmark: () -> Unit
) {
    val date = uiState.earnedOnPriorDate
    val formatter = rememberCompletionDateFormatter()
    // Its text, unlike a button's, sits at the top of its space.
    val dateModifier = Modifier.padding(top = 16.dp)
    when {
        date != null -> EditableDate(
            text = stringResource(R.string.rank_detail_earned_on, formatter.format(date)),
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

        uiState.status != RankStatus.Earned -> Column {
            uiState.waitingOn?.let {
                StatusText(stringResource(R.string.rank_detail_waiting_on, it))
            }
            PickDateButton(
                text = stringResource(R.string.rank_detail_mark_earned),
                // At the date the scout just unmarked, if any, so a mistaken Unmark loses nothing.
                initial = uiState.unmarkedDate,
                today = today,
                onPick = onMark,
                // Lines the button's text up with the page's.
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // Earned from its requirements. Room under it, as a button would leave.
        else -> StatusText(
            text = uiState.earnedOn?.let {
                stringResource(R.string.rank_detail_earned_on, formatter.format(it))
            } ?: stringResource(R.string.rank_detail_earned),
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

/** A line about how the rank is earned, styled as [EditableDate]'s. */
@Composable
private fun StatusText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
    )
}
