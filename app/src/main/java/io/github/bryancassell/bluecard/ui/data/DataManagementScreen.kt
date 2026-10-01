package io.github.bryancassell.bluecard.ui.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import io.github.bryancassell.bluecard.data.backup.exportFileName
import io.github.bryancassell.bluecard.ui.ConfirmDialog
import io.github.bryancassell.bluecard.ui.MessageSnackbarHost
import io.github.bryancassell.bluecard.ui.data.DataManagementMessage.Kind
import io.github.bryancassell.bluecard.ui.rememberStartOtherApp
import java.time.LocalDate

/** Connects the Data management screen to its ViewModel. */
@Composable
fun DataManagementRoute(
    modifier: Modifier = Modifier,
    viewModel: DataManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DataManagementScreen(
        uiState = uiState,
        today = viewModel::today,
        onExport = viewModel::export,
        onImport = viewModel::read,
        onConfirmImport = viewModel::confirmImport,
        onCancelImport = viewModel::cancelImport,
        onMessageShown = viewModel::onMessageShown,
        modifier = modifier
    )
}

/**
 * Export and import of the scout's data. Export saves it to a file the scout creates with the
 * system file picker ([onExport]), suggesting a name with [today]'s date, read as it opens. Import reads a file they pick ([onImport]) and, once it's
 * checked, asks before replacing everything with it ([onConfirmImport]).
 */
@Composable
fun DataManagementScreen(
    uiState: DataManagementUiState,
    today: () -> LocalDate,
    onExport: (destination: Uri) -> Unit,
    onImport: (source: Uri) -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onMessageShown: (DataManagementMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    val startOtherApp = rememberStartOtherApp()
    // Each is null when the scout leaves the file picker without choosing a file.
    val createDocument = rememberLauncherForActivityResult(CreateDocument(JSON)) { destination ->
        destination?.let(onExport)
    }
    val openDocument = rememberLauncherForActivityResult(OpenOpenableDocument()) { source ->
        source?.let(onImport)
    }
    val resources = LocalResources.current
    val noFileSaver = stringResource(R.string.no_file_saver)
    val noFileOpener = stringResource(R.string.data_management_no_file_opener)
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.data_management_title),
                style = MaterialTheme.typography.headlineMedium,
                // Lets screen reader users jump to it.
                modifier = Modifier.semantics { heading() }
            )
            Section(
                heading = R.string.data_management_export_heading,
                description = R.string.data_management_export_description,
                button = R.string.data_management_export,
                enabled = !uiState.working,
                onClick = {
                    val fileName = exportFileName(resources, today())
                    startOtherApp.launch(createDocument, fileName, noFileSaver)
                }
            )
            Section(
                heading = R.string.data_management_import_heading,
                description = R.string.data_management_import_description,
                button = R.string.data_management_import,
                enabled = !uiState.working,
                onClick = { startOtherApp.launch(openDocument, IMPORT_TYPES, noFileOpener) }
            )
        }
        val message = uiState.message
        MessageSnackbarHost(
            message = message,
            text = message?.let { stringResource(it.kind.text) }.orEmpty(),
            onShown = onMessageShown,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
    if (uiState.backupToImport != null) {
        ConfirmDialog(
            title = stringResource(R.string.import_confirm_title),
            message = stringResource(R.string.import_confirm_message),
            confirmLabel = stringResource(R.string.import_confirm),
            onConfirm = onConfirmImport,
            onDismiss = onCancelImport
        )
    }
}

/** A heading, what it does, and a button that does it. */
@Composable
private fun Section(
    @StringRes heading: Int,
    @StringRes description: Int,
    @StringRes button: Int,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = stringResource(heading),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(top = 16.dp)
            .semantics { heading() }
    )
    Text(text = stringResource(description), style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = onClick, enabled = enabled) {
        Text(stringResource(button))
    }
}

private val Kind.text: Int
    @StringRes get() = when (this) {
        Kind.ExportFailed -> R.string.export_failed
        Kind.ReadFailed -> R.string.import_read_failed
        Kind.Invalid -> R.string.import_invalid
        Kind.NewerFormat -> R.string.import_newer_format
        Kind.ImportFailed -> R.string.import_failed
        Kind.Imported -> R.string.import_done
    }

private const val JSON = "application/json"

/**
 * Opens a document that can be read as a file, as the [ACTION_OPEN_DOCUMENT] docs say to ask
 * for: the file picker leaves out virtual documents, such as a Google Doc, which can't be.
 *
 * [ACTION_OPEN_DOCUMENT]: https://developer.android.com/reference/android/content/Intent#ACTION_OPEN_DOCUMENT
 */
private class OpenOpenableDocument : OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).addCategory(Intent.CATEGORY_OPENABLE)
}

/**
 * Any file. An export copied to the phone some other way may not be labeled as JSON, and the
 * file picker doesn't let the scout choose a file it filters out. A file that isn't an export
 * is rejected once it's read.
 */
private val IMPORT_TYPES = arrayOf("*/*")
