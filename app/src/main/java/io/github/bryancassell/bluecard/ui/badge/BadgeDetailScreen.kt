package io.github.bryancassell.bluecard.ui.badge

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.data.progress.BadgeStatus
import io.github.bryancassell.bluecard.data.report.ReportKind
import io.github.bryancassell.bluecard.data.report.reportFileName
import io.github.bryancassell.bluecard.ui.LoadingOrMessage
import io.github.bryancassell.bluecard.ui.TaskFailure
import io.github.bryancassell.bluecard.ui.TaskFailureSnackbar
import io.github.bryancassell.bluecard.ui.badges.eagleRequirementLabel
import io.github.bryancassell.bluecard.ui.badges.rememberBadgeNameListFormatter
import io.github.bryancassell.bluecard.ui.rememberOtherAppStarter
import java.time.LocalDate

/** Connects the Badge detail screen to its ViewModel. */
@Composable
fun BadgeDetailRoute(
    badgeId: String,
    onOpenRequirement: (number: String) -> Unit,
    onEditCounselor: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BadgeDetailViewModel =
        hiltViewModel<BadgeDetailViewModel, BadgeDetailViewModel.Factory> { it.create(badgeId) }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BadgeDetailScreen(
        uiState = uiState,
        onOpenRequirement = onOpenRequirement,
        onEditCounselor = onEditCounselor,
        today = viewModel::today,
        onMarkCompleted = viewModel::markCompleted,
        onUnmarkCompleted = viewModel::unmarkCompleted,
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
 * A badge's summary, how much of it is done while it's in progress, whether it's
 * Eagle-required, a link to its official page, the scout's merit badge counselor, and its
 * top-level requirements. Each requirement opens its own page, where the scout marks it
 * complete, and the counselor is entered on a page of its own, which keeps this one short.
 *
 * While the badge isn't complete, the scout can mark it completed on a date they pick, up to
 * [today], without recording its requirements, as for a badge earned before they used the app
 * ([onMarkCompleted]). The date then shows, and they can change it or unmark the badge
 * ([onUnmarkCompleted]).
 *
 * Once the badge is complete, its report can be shared, which asks for it to be created
 * ([onShareReport]) and opens the share sheet once it's ready ([onReportShared]), or saved,
 * which asks the scout where with the system file picker ([onSaveReport]). At the bottom, once
 * the badge is started, the scout can clear its progress ([onClear]).
 */
@Composable
fun BadgeDetailScreen(
    uiState: BadgeDetailUiState,
    onOpenRequirement: (number: String) -> Unit,
    onEditCounselor: () -> Unit,
    today: () -> LocalDate,
    onMarkCompleted: (date: LocalDate) -> Unit,
    onUnmarkCompleted: () -> Unit,
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
        BadgeDetailUiState.Loading,
        BadgeDetailUiState.LoadFailed,
        BadgeDetailUiState.Unavailable -> LoadingOrMessage(
            message = when (uiState) {
                BadgeDetailUiState.LoadFailed -> stringResource(R.string.load_failed)
                BadgeDetailUiState.Unavailable -> stringResource(R.string.requirements_unavailable)
                else -> null
            },
            modifier = modifier
        )

        is BadgeDetailUiState.Ready -> Box(modifier = modifier) {
            BadgeDetails(
                uiState,
                onOpenRequirement,
                onEditCounselor,
                today,
                onMarkCompleted,
                onUnmarkCompleted,
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
private fun BadgeDetails(
    uiState: BadgeDetailUiState.Ready,
    onOpenRequirement: (number: String) -> Unit,
    onEditCounselor: () -> Unit,
    today: () -> LocalDate,
    onMarkCompleted: (date: LocalDate) -> Unit,
    onUnmarkCompleted: () -> Unit,
    onShareReport: () -> Unit,
    onReportShared: () -> Unit,
    onSaveReport: (destination: Uri) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Opens the official page in the browser. The counselor's phone and email and the report
    // share it, so quick taps on any of them open one app, once.
    val startOtherApp = rememberOtherAppStarter()
    uiState.reportToShare?.let { ShareReport(it, onReportShared) }
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AdvancementHeader(
            name = uiState.name,
            fractionDone = uiState.fractionDone,
            summary = uiState.summary,
            officialUrl = uiState.officialUrl,
            startOtherApp = startOtherApp,
            tag = uiState.eagle?.let {
                { EagleTag(eagleRequirementLabel(it, rememberBadgeNameListFormatter())) }
            }
        )
        CompletionStatus(uiState, today, onMarkCompleted, onUnmarkCompleted) {
            ReportButtons(
                reportFileName(LocalResources.current, ReportKind.MeritBadge, uiState.name),
                onShareReport,
                onSaveReport,
                startOtherApp
            )
        }
        CounselorSection(uiState.counselor, onEdit = onEditCounselor, startOtherApp = startOtherApp)
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
                message = stringResource(
                    if (uiState.counselor == null) {
                        R.string.badge_detail_clear_message
                    } else {
                        R.string.badge_detail_clear_message_with_counselor
                    }
                ),
                unearnedRanks = uiState.unearnedByClear,
                onClear = onClear
            )
        }
    }
}

/**
 * The badge's [StatusCard]:
 * - While it isn't complete, "In progress" once it's started, as on Badges, or "Not started",
 *   with a button to mark it completed on a date the scout picks, up to [today], without
 *   recording its requirements ([onMark]). Its picker opens at the date the scout just
 *   unmarked, if any, or at today.
 * - Once it's marked, the date, with buttons to change it ([onMark]) or unmark the badge
 *   ([onUnmark]).
 * - For a badge complete from its requirements, the date they were completed on.
 *
 * Once it's complete, the [reportButtons] follow.
 */
@Composable
private fun CompletionStatus(
    uiState: BadgeDetailUiState.Ready,
    today: () -> LocalDate,
    onMark: (date: LocalDate) -> Unit,
    onUnmark: () -> Unit,
    reportButtons: @Composable () -> Unit
) {
    val date = uiState.completedOnPriorDate
    val formatter = rememberCompletionDateFormatter()
    StatusCard(reportButtons = reportButtons.takeIf { uiState.completed }) {
        when {
            date != null -> EditableDate(
                text = stringResource(R.string.badge_detail_completed_on, formatter.format(date)),
                date = date,
                today = today,
                onDateChange = { if (it == null) onUnmark() else onMark(it) },
                removeText = R.string.badge_detail_unmark_completed,
                textStyle = statusLineStyle
            )

            uiState.completed -> DoneStatusLine(
                uiState.completedOn?.let {
                    stringResource(R.string.badge_detail_completed_on, formatter.format(it))
                } ?: stringResource(R.string.badges_completed)
            )

            else -> MarkDoneLines(
                status = stringResource(
                    if (uiState.status == BadgeStatus.InProgress) {
                        R.string.badges_in_progress
                    } else {
                        R.string.badge_detail_not_started
                    }
                ),
                prompt = stringResource(R.string.badge_detail_mark_completed_prompt),
                markText = stringResource(R.string.badge_detail_mark_completed),
                // At the date the scout just unmarked, if any, so a mistaken Unmark loses nothing.
                initial = uiState.unmarkedDate,
                today = today,
                onMark = onMark
            )
        }
    }
}

/**
 * Says the badge is Eagle-required, as a filled tag. Its small corners keep it from looking like
 * the buttons near it, which are fully rounded. A long label wraps inside it.
 */
@Composable
private fun EagleTag(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryFixedDim,
        contentColor = MaterialTheme.colorScheme.onPrimaryFixed,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val style = MaterialTheme.typography.labelLarge
            // A box one line tall keeps the icon centered on the first line at any font size.
            // Where Android scales large text up less, Compose keeps a line's height in proportion
            // to its font size rather than scaling it on its own, so the box does too.
            val lineHeight = with(LocalDensity.current) { style.fontSize.toDp() } *
                (style.lineHeight.value / style.fontSize.value)
            Box(modifier = Modifier.height(lineHeight), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_workspace_premium),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(text = label, style = style)
        }
    }
}
