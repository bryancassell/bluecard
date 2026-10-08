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
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import io.github.bryancassell.bluecard.ui.ButtonText
import io.github.bryancassell.bluecard.ui.ConfirmDialog
import io.github.bryancassell.bluecard.ui.MessageSnackbarHost
import io.github.bryancassell.bluecard.ui.data.DataManagementMessage.Kind
import io.github.bryancassell.bluecard.ui.rememberOtherAppStarter
import io.github.bryancassell.bluecard.ui.removalOutlinedButtonColors
import java.time.LocalDate

/** Connects the Data management screen to its ViewModel. */
@Composable
fun DataManagementRoute(
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DataManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DataManagementScreen(
        uiState = uiState,
        today = viewModel::today,
        onEditProfile = onEditProfile,
        onExport = viewModel::export,
        onImport = viewModel::read,
        onMerge = viewModel::merge,
        onReplace = viewModel::replace,
        onChooseProfile = viewModel::chooseProfile,
        onChooseProgress = viewModel::chooseProgress,
        onConfirmMerge = viewModel::confirmMerge,
        onCancelImport = viewModel::cancelImport,
        onClearAll = viewModel::clearAll,
        onMessageShown = viewModel::onMessageShown,
        modifier = modifier
    )
}

/**
 * Changing the scout's name and unit number, export and import of the scout's data, and
 * clearing all their progress. Edit opens a page to change the name and unit number
 * ([onEditProfile]). Export saves the data to a file the scout creates with the system file
 * picker ([onExport]), suggesting a name with [today]'s date, read as it opens. Import reads a
 * file they pick ([onImport]) and, once it's checked, asks whether to merge it with their data
 * ([onMerge]) or replace everything with it ([onReplace]). A merge then asks which to keep, the
 * phone's or the file's, for the name and unit number ([onChooseProfile]) and each badge and
 * rank ([onChooseProgress]) where the two differ, before merging ([onConfirmMerge]). Clear all
 * asks before clearing every badge's progress ([onClearAll]).
 */
@Composable
fun DataManagementScreen(
    uiState: DataManagementUiState,
    today: () -> LocalDate,
    onEditProfile: () -> Unit,
    onExport: (destination: Uri) -> Unit,
    onImport: (source: Uri) -> Unit,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
    onChooseProfile: (fromFile: Boolean) -> Unit,
    onChooseProgress: (id: String, fromFile: Boolean) -> Unit,
    onConfirmMerge: () -> Unit,
    onCancelImport: () -> Unit,
    onClearAll: () -> Unit,
    onMessageShown: (DataManagementMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    val startOtherApp = rememberOtherAppStarter()
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
    var confirmingClear by rememberSaveable { mutableStateOf(false) }
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
                heading = R.string.data_management_profile_heading,
                description = R.string.data_management_profile_description,
                button = R.string.data_management_profile_edit,
                // Screen reader users moving from control to control don't hear the heading.
                buttonDescription = R.string.data_management_profile_edit_description,
                enabled = !uiState.working,
                // Through the screen's OtherAppStarter, so a tap just after Export or Import
                // doesn't open the page under the file picker.
                onClick = { startOtherApp.tap(onEditProfile) }
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
            // Red, and last on the page, so it isn't tapped by mistake.
            Section(
                heading = R.string.data_management_clear_heading,
                description = R.string.data_management_clear_description,
                button = R.string.data_management_clear,
                enabled = uiState.canClear && !uiState.working,
                // Through the screen's OtherAppStarter, so a tap just after Export or Import
                // doesn't open the dialog under the file picker, to be confirmed after it.
                onClick = { startOtherApp.tap { confirmingClear = true } },
                colors = removalOutlinedButtonColors()
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
        ImportDialog(onMerge = onMerge, onReplace = onReplace, onDismiss = onCancelImport)
    }
    uiState.mergeChoices?.let { choices ->
        MergeDialog(
            choices = choices,
            onChooseProfile = onChooseProfile,
            onChooseProgress = onChooseProgress,
            onConfirm = onConfirmMerge,
            onDismiss = onCancelImport
        )
    }
    if (confirmingClear) {
        ConfirmDialog(
            title = stringResource(R.string.clear_all_confirm_title),
            message = stringResource(R.string.clear_all_confirm_message),
            confirmLabel = stringResource(R.string.clear_progress_confirm),
            onConfirm = {
                confirmingClear = false
                onClearAll()
            },
            onDismiss = { confirmingClear = false }
        )
    }
}

/**
 * A heading, what it does, and a button that does it. Screen readers read [buttonDescription]
 * in place of the button's text, unless it's null.
 */
@Composable
private fun Section(
    @StringRes heading: Int,
    @StringRes description: Int,
    @StringRes button: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    @StringRes buttonDescription: Int? = null
) {
    Text(
        text = stringResource(heading),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(top = 16.dp)
            .semantics { heading() }
    )
    Text(text = stringResource(description), style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = onClick, enabled = enabled, colors = colors) {
        ButtonText(
            text = stringResource(button),
            description = buttonDescription?.let { stringResource(it) }
        )
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
        Kind.Merged -> R.string.merge_done
        Kind.ClearFailed -> R.string.save_failed
        Kind.Cleared -> R.string.clear_all_done
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
