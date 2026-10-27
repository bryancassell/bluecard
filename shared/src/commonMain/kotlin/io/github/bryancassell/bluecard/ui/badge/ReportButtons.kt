package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.bryancassell.bluecard.data.DocumentUri
import io.github.bryancassell.bluecard.data.report.reportFileName
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.badge_detail_save_report
import io.github.bryancassell.bluecard.resources.badge_detail_share_report
import io.github.bryancassell.bluecard.resources.no_file_saver
import io.github.bryancassell.bluecard.ui.OtherAppStarter
import io.github.bryancassell.bluecard.ui.rememberCreateDocument
import io.github.bryancassell.bluecard.ui.rememberOtherApps
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// A badge's or rank's report, on its page once it's complete or earned.

private const val PDF = "application/pdf"

/**
 * Buttons that share a badge's or rank's report through the share sheet ([onShare]), or save it
 * where the scout chooses with the system file picker ([onSave]), which suggests [fileName]
 * ([reportFileName]).
 */
@Composable
fun ReportButtons(
    fileName: String,
    onShare: () -> Unit,
    onSave: (destination: DocumentUri) -> Unit,
    startOtherApp: OtherAppStarter,
    modifier: Modifier = Modifier
) {
    val createDocument = rememberCreateDocument(PDF, onCreated = onSave)
    val noFilePicker = stringResource(Res.string.no_file_saver)
    // Wraps the buttons onto two lines when they don't fit on one, as with large text.
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReportButton(Res.string.badge_detail_share_report, onClick = { startOtherApp.tap(onShare) })
        ReportButton(
            Res.string.badge_detail_save_report,
            onClick = { startOtherApp.launch(createDocument, fileName, noFilePicker) }
        )
    }
}

/** A button for the report, outlined as the official link is. */
@Composable
private fun ReportButton(text: StringResource, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = detailOutlinedButtonColors(),
        border = detailOutlinedButtonBorder()
    ) {
        Text(stringResource(text))
    }
}

/**
 * Opens the share sheet with a badge's or rank's [report], once, then calls [onShared]. The
 * Share report tap already went through the screen's [OtherAppStarter], so this doesn't, and the
 * share sheet is always there to start. The report waits in UI state, so if the page is covered
 * before it's ready, as by another app or a requirement's page, the share sheet opens once the
 * page shows again. Going back from the page clears its ViewModel, and the report with it.
 */
@Composable
fun ShareReport(report: DocumentUri, onShared: () -> Unit) {
    val otherApps = rememberOtherApps()
    val currentOnShared by rememberUpdatedState(onShared)
    LaunchedEffect(report) {
        otherApps.share(report, PDF)
        currentOnShared()
    }
}
