package io.github.bryancassell.bluecard.ui.badge

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.ui.LoadFailedMessage
import io.github.bryancassell.bluecard.ui.OtherAppStarter
import io.github.bryancassell.bluecard.ui.SaveFailedSnackbarHost
import io.github.bryancassell.bluecard.ui.SaveFailure
import io.github.bryancassell.bluecard.ui.ScreenMessage
import io.github.bryancassell.bluecard.ui.badges.eagleRequirementLabel
import io.github.bryancassell.bluecard.ui.badges.rememberBadgeNameListFormatter
import io.github.bryancassell.bluecard.ui.rememberStartOtherApp

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
        onShareReport = viewModel::shareReport,
        onReportShared = viewModel::onReportShared,
        onSaveReport = viewModel::saveReport,
        onReportFailureShown = viewModel::onReportFailureShown,
        modifier = modifier
    )
}

/**
 * A badge's summary, whether it's Eagle-required, a link to its official page, the scout's
 * merit badge counselor, and its top-level requirements. Each requirement opens its own page,
 * where the scout marks it complete, and the counselor is entered on a page of its own, which
 * keeps this one short.
 *
 * Once the badge is complete, its report can be shared, which asks for it to be created
 * ([onShareReport]) and opens the share sheet once it's ready ([onReportShared]), or saved,
 * which asks the scout where with the system file picker ([onSaveReport]).
 */
@Composable
fun BadgeDetailScreen(
    uiState: BadgeDetailUiState,
    onOpenRequirement: (number: String) -> Unit,
    onEditCounselor: () -> Unit,
    onShareReport: () -> Unit,
    onReportShared: () -> Unit,
    onSaveReport: (destination: Uri) -> Unit,
    onReportFailureShown: (SaveFailure) -> Unit,
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
                onShareReport,
                onReportShared,
                onSaveReport
            )
            SaveFailedSnackbarHost(
                failure = uiState.reportFailure,
                onShown = onReportFailureShown,
                modifier = Modifier.align(Alignment.BottomCenter),
                message = stringResource(R.string.report_failed)
            )
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
    onShareReport: () -> Unit,
    onReportShared: () -> Unit,
    onSaveReport: (destination: Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    // Opens the official page in the browser. The counselor's phone and email and the report
    // share it, so quick taps on any of them open one app, once.
    val startOtherApp = rememberStartOtherApp()
    uiState.reportToShare?.let { ShareReport(it, startOtherApp, onReportShared) }
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = uiState.name,
                style = MaterialTheme.typography.headlineMedium,
                // Lets screen reader users jump to it.
                modifier = Modifier.semantics { heading() }
            )
            uiState.eagle?.let {
                Text(
                    text = eagleRequirementLabel(it, rememberBadgeNameListFormatter()),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(text = uiState.summary, style = MaterialTheme.typography.bodyLarge)
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
            if (uiState.completed) {
                ReportButtons(uiState.name, onShareReport, onSaveReport, startOtherApp)
            }
        }
        CounselorSection(uiState.counselor, onEdit = onEditCounselor, startOtherApp = startOtherApp)
        Text(
            text = stringResource(R.string.badge_detail_requirements),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { heading() }
        )
        uiState.requirements.forEach {
            RequirementRow(item = it, onOpen = onOpenRequirement)
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
    startOtherApp: OtherAppStarter
) {
    val createDocument = rememberLauncherForActivityResult(CreateDocument(PDF)) { destination ->
        // Null when the scout leaves the file picker without saving.
        destination?.let(onSave)
    }
    val fileName = stringResource(R.string.report_file_name, badgeName) + ".pdf"
    val noFilePicker = stringResource(R.string.badge_detail_no_file_picker)
    // Wraps the buttons onto two lines when they don't fit on one, as with large text.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = onShare) {
            Text(stringResource(R.string.badge_detail_share_report))
        }
        // Outlined as the official link is.
        OutlinedButton(
            onClick = { startOtherApp.launch(createDocument, fileName, noFilePicker) },
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            ),
            border = ButtonDefaults.outlinedButtonBorder()
                .copy(brush = SolidColor(MaterialTheme.colorScheme.outline))
        ) {
            Text(stringResource(R.string.badge_detail_save_report))
        }
    }
}

/** Opens the share sheet with the badge's [report], once, then calls [onShared]. */
@Composable
private fun ShareReport(report: Uri, startOtherApp: OtherAppStarter, onShared: () -> Unit) {
    val noApp = stringResource(R.string.badge_detail_no_share_app)
    val currentOnShared by rememberUpdatedState(onShared)
    LaunchedEffect(report) {
        startOtherApp(shareIntent(report), noApp)
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
