package io.github.bryancassell.bluecard.ui.badge

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.data.report.reportFileName
import io.github.bryancassell.bluecard.ui.LoadFailedMessage
import io.github.bryancassell.bluecard.ui.OtherAppStarter
import io.github.bryancassell.bluecard.ui.SaveFailure
import io.github.bryancassell.bluecard.ui.SaveFailureSnackbar
import io.github.bryancassell.bluecard.ui.ScreenMessage
import io.github.bryancassell.bluecard.ui.badges.BadgeProgressBar
import io.github.bryancassell.bluecard.ui.badges.eagleRequirementLabel
import io.github.bryancassell.bluecard.ui.badges.percentDoneDescription
import io.github.bryancassell.bluecard.ui.badges.rememberBadgeNameListFormatter
import io.github.bryancassell.bluecard.ui.rememberStartOtherApp
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
    onReportFailureShown: (SaveFailure) -> Unit,
    onClear: () -> Unit,
    onSaveFailureShown: (SaveFailure) -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        BadgeDetailUiState.Loading -> LoadingIndicator(modifier)

        BadgeDetailUiState.LoadFailed -> LoadFailedMessage(modifier)

        BadgeDetailUiState.Unavailable ->
            ScreenMessage(stringResource(R.string.requirements_unavailable), modifier)

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
            SaveFailureSnackbar(
                failure = uiState.reportFailure,
                onShown = onReportFailureShown,
                hostState = snackbarHostState,
                message = stringResource(R.string.report_failed)
            )
            SaveFailureSnackbar(uiState.saveFailure, onSaveFailureShown, snackbarHostState)
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Test tag of the official requirements link's icon, which has no semantics of its own. */
internal const val OFFICIAL_LINK_ICON_TAG = "officialLinkIcon"

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
    val startOtherApp = rememberStartOtherApp()
    uiState.reportToShare?.let { ShareReport(it, onReportShared) }
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = uiState.name,
                style = MaterialTheme.typography.headlineMedium,
                // Lets screen reader users jump to it.
                modifier = Modifier.semantics { heading() }
            )
            uiState.fractionDone?.let {
                val inProgress = stringResource(R.string.badges_in_progress)
                val percentDone = percentDoneDescription(it)
                // Read as on the badge's row: TalkBack says "40% done. In progress".
                BadgeProgressBar(
                    fractionDone = it,
                    modifier = Modifier.semantics {
                        contentDescription = inProgress
                        stateDescription = percentDone
                    }
                )
            }
            uiState.eagle?.let {
                EagleTag(eagleRequirementLabel(it, rememberBadgeNameListFormatter()))
            }
            Text(text = uiState.summary, style = MaterialTheme.typography.bodyLarge)
            // ACTION_VIEW, as Compose's UriHandler uses, but started by the screen's
            // OtherAppStarter, so it handles a double tap and "no app" as the counselor's
            // phone and email do.
            val officialPage = Intent(Intent.ACTION_VIEW, uiState.officialUrl.toUri())
            // Shown when no app can open web links, as when parental controls block the browser.
            val noBrowser = stringResource(R.string.badge_detail_no_browser)
            val openInBrowser = stringResource(R.string.badge_detail_open_in_browser)
            // Outlined, with an "open in new" icon, so it stands out and says it leaves the app.
            // The theme's outline color, rather than Material's lighter default, keeps the outline
            // visible on the tinted background, and the label is primary blue, like a link.
            OutlinedButton(
                onClick = { startOtherApp(officialPage, noBrowser) },
                // The button keeps its own click action, with this label.
                modifier = Modifier.semantics { onClick(label = openInBrowser, action = null) },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                border = ButtonDefaults.outlinedButtonBorder()
                    .copy(brush = SolidColor(MaterialTheme.colorScheme.outline)),
                // Material's padding for an icon before the label, flipped for one after it.
                contentPadding = with(ButtonDefaults.ButtonWithIconContentPadding) {
                    PaddingValues(
                        start = calculateEndPadding(LayoutDirection.Ltr),
                        top = calculateTopPadding(),
                        end = calculateStartPadding(LayoutDirection.Ltr),
                        bottom = calculateBottomPadding()
                    )
                }
            ) {
                Text(text = stringResource(R.string.badge_detail_official_page))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Icon(
                    painterResource(R.drawable.ic_open_in_new),
                    contentDescription = null,
                    modifier = Modifier
                        .size(ButtonDefaults.IconSize)
                        .testTag(OFFICIAL_LINK_ICON_TAG)
                )
            }
        }
        // Outside the column above, so its text buttons line up with the page's text, as Add
        // counselor's does.
        CompletedOnPriorDate(uiState, today, onMarkCompleted, onUnmarkCompleted)
        if (uiState.completed) {
            ReportButtons(
                uiState.name,
                onShareReport,
                onSaveReport,
                startOtherApp,
                Modifier.padding(
                    start = 16.dp,
                    // Under Change date, whose touch area already leaves room below its text.
                    top = if (uiState.completedOnPriorDate == null) 8.dp else 0.dp,
                    end = 16.dp,
                    bottom = 16.dp
                )
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
                onClear = onClear
            )
        }
    }
}

/**
 * While the badge isn't complete, a button to mark it completed on a date the scout picks, up to
 * [today], without recording its requirements ([onMark]). Once it's marked, the date, with
 * buttons to change it ([onMark]) or unmark the badge ([onUnmark]). Nothing for a badge complete
 * from its requirements alone.
 *
 * Its text buttons' touch areas are taller than they look, so they need no padding of their own
 * to keep it apart from what's above and below.
 */
@Composable
private fun CompletedOnPriorDate(
    uiState: BadgeDetailUiState.Ready,
    today: () -> LocalDate,
    onMark: (date: LocalDate) -> Unit,
    onUnmark: () -> Unit
) {
    val date = uiState.completedOnPriorDate
    if (date != null) {
        val formatter = rememberCompletionDateFormatter()
        EditableDate(
            text = stringResource(R.string.badge_detail_completed_on, formatter.format(date)),
            date = date,
            today = today,
            onDateChange = { if (it == null) onUnmark() else onMark(it) },
            // Its text, unlike a button's, sits at the top of its space.
            modifier = Modifier.padding(top = 16.dp),
            removeText = R.string.badge_detail_unmark_completed
        )
    } else if (!uiState.completed) {
        var picking by rememberSaveable { mutableStateOf(false) }
        // Lines the button's text up with the page's, as for Add counselor.
        TextButton(onClick = { picking = true }, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(stringResource(R.string.badge_detail_mark_completed))
        }
        if (picking) {
            // Read as the picker opens, so a page left open past midnight offers the new day.
            val latest = remember { today() }
            CompletionDatePickerDialog(
                initial = latest,
                today = latest,
                onConfirm = {
                    picking = false
                    onMark(it)
                },
                onDismiss = { picking = false }
            )
        }
    }
}

/**
 * Says the badge is Eagle-required, as a filled tag. It's the only filled shape on the page, with
 * small corners, so it doesn't look like the buttons near it, which are outlined or plain text and
 * fully rounded. A long label wraps inside it.
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

private const val PDF = "application/pdf"

/**
 * Buttons that share the badge's report through the share sheet ([onShare]), or save it where
 * the scout chooses with the system file picker ([onSave]).
 */
@Composable
private fun ReportButtons(
    badgeName: String,
    onShare: () -> Unit,
    onSave: (destination: Uri) -> Unit,
    startOtherApp: OtherAppStarter,
    modifier: Modifier = Modifier
) {
    val createDocument = rememberLauncherForActivityResult(CreateDocument(PDF)) { destination ->
        // Null when the scout leaves the file picker without saving.
        destination?.let(onSave)
    }
    val fileName = reportFileName(LocalResources.current, badgeName)
    val noFilePicker = stringResource(R.string.no_file_saver)
    // Wraps the buttons onto two lines when they don't fit on one, as with large text.
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReportButton(R.string.badge_detail_share_report, onClick = { startOtherApp.tap(onShare) })
        ReportButton(
            R.string.badge_detail_save_report,
            onClick = { startOtherApp.launch(createDocument, fileName, noFilePicker) }
        )
    }
}

/**
 * A button for the report, outlined as the official link is, so the Eagle-required tag stays
 * the only filled shape on the page.
 */
@Composable
private fun ReportButton(@StringRes text: Int, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        ),
        border = ButtonDefaults.outlinedButtonBorder()
            .copy(brush = SolidColor(MaterialTheme.colorScheme.outline))
    ) {
        Text(stringResource(text))
    }
}

/**
 * Opens the share sheet with the badge's [report], once, then calls [onShared]. The Share
 * report tap already went through the screen's [OtherAppStarter], so this doesn't, and the
 * share sheet is always there to start. The report waits in UI state, so if Badge detail is
 * covered before it's ready, as by another app or a requirement's page, the share sheet opens
 * once Badge detail shows again. Going back from Badge detail clears its ViewModel, and the
 * report with it.
 */
@Composable
private fun ShareReport(report: Uri, onShared: () -> Unit) {
    val context = LocalContext.current
    val currentOnShared by rememberUpdatedState(onShared)
    LaunchedEffect(report) {
        context.startActivity(shareIntent(report))
        currentOnShared()
    }
}

/**
 * The share sheet, to send [report] to the app the scout picks. The clip data lets the share
 * sheet show the file, and the flag grants the app picked permission to read it:
 * https://developer.android.com/training/sharing/send#adding-rich-content-previews
 */
private fun shareIntent(report: Uri): Intent {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = PDF
        putExtra(Intent.EXTRA_STREAM, report)
        clipData = ClipData.newRawUri(null, report)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, null)
}
