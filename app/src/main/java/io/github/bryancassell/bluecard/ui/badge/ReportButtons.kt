package io.github.bryancassell.bluecard.ui.badge

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.data.report.reportFileName
import io.github.bryancassell.bluecard.ui.OtherAppStarter

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
    onSave: (destination: Uri) -> Unit,
    startOtherApp: OtherAppStarter,
    modifier: Modifier = Modifier
) {
    val createDocument = rememberLauncherForActivityResult(CreateDocument(PDF)) { destination ->
        // Null when the scout leaves the file picker without saving.
        destination?.let(onSave)
    }
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

/** A button for the report, outlined as the official link is. */
@Composable
private fun ReportButton(@StringRes text: Int, onClick: () -> Unit) {
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
fun ShareReport(report: Uri, onShared: () -> Unit) {
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
