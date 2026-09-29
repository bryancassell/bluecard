package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.then
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.ui.LoadFailedMessage
import io.github.bryancassell.bluecard.ui.NumberInput
import io.github.bryancassell.bluecard.ui.SaveFailedSnackbarHost
import io.github.bryancassell.bluecard.ui.SaveFailure
import io.github.bryancassell.bluecard.ui.ScreenMessage
import io.github.bryancassell.bluecard.ui.TextLengthLimit
import io.github.bryancassell.bluecard.ui.typedTextFieldStyle
import java.time.LocalDate

/** Connects the Tracker entry screen to its ViewModel. */
@Composable
fun TrackerEntryRoute(
    badgeId: String,
    number: String,
    entryId: Long?,
    rowNumber: Int?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrackerEntryViewModel =
        hiltViewModel<TrackerEntryViewModel, TrackerEntryViewModel.Factory> {
            it.create(badgeId, number, entryId, rowNumber)
        }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TrackerEntryScreen(
        uiState = uiState,
        fields = viewModel.fields,
        onDateChange = viewModel::setDate,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onClose = onClose,
        onSaveFailureShown = viewModel::onSaveFailureShown,
        modifier = modifier
    )
}

/**
 * One row of a requirement's tracker, to fill in or change: a field for each of the tracker's
 * columns, in the text [fields] or, for a date, with a date picker. Save and Delete close the
 * page ([onClose]) once they're done; Delete asks first.
 */
@Composable
fun TrackerEntryScreen(
    uiState: TrackerEntryUiState,
    fields: Map<String, TextFieldState>,
    onDateChange: (columnId: String, date: LocalDate?) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    onSaveFailureShown: (SaveFailure) -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        TrackerEntryUiState.Loading -> LoadingIndicator(modifier)

        TrackerEntryUiState.LoadFailed -> LoadFailedMessage(modifier)

        TrackerEntryUiState.Unavailable ->
            ScreenMessage(stringResource(R.string.tracker_entry_unavailable), modifier)

        is TrackerEntryUiState.Ready -> {
            if (uiState.done) {
                val currentOnClose by rememberUpdatedState(onClose)
                LaunchedEffect(Unit) { currentOnClose() }
            }
            // Ends the page above the keyboard, so every field can be scrolled into view.
            Box(modifier = modifier.imePadding()) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TrackerEntryHeader(uiState)
                    uiState.columns.forEach { column ->
                        // Keeps each date field's picker with its column.
                        key(column.id) {
                            TrackerField(
                                column = column,
                                field = fields.getValue(column.id),
                                date = uiState.dates[column.id],
                                today = uiState.today,
                                onDateChange = { onDateChange(column.id, it) }
                            )
                        }
                    }
                    TrackerEntryButtons(uiState, onSave, onDelete)
                }
                SaveFailedSnackbarHost(
                    failure = uiState.saveFailure,
                    onShown = onSaveFailureShown,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun TrackerEntryHeader(uiState: TrackerEntryUiState.Ready) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = uiState.badgeName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.requirement_detail_title, uiState.requirementNumber),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.tracker_row_title, uiState.rowTitle, uiState.rowNumber),
            style = MaterialTheme.typography.headlineMedium,
            // Lets screen reader users jump to it.
            modifier = Modifier.semantics { heading() }
        )
    }
}

/**
 * Plenty for a note in a tracker row. The fields' text is saved with the screen's state, which
 * has a size limit, so a huge paste mustn't reach it.
 */
private val TextLimit = TextLengthLimit(maxLength = 500)

/** Longer than any number a scout would log. */
private val NumberLimit = NumberInput.then(TextLengthLimit(maxLength = 20))

/** The field for one column: a date with a picker, or a text field for text or a number. */
@Composable
private fun TrackerField(
    column: TrackerColumn,
    field: TextFieldState,
    date: LocalDate?,
    today: LocalDate,
    onDateChange: (LocalDate?) -> Unit
) {
    when (column.type) {
        TrackerColumnType.DATE -> Column {
            Text(
                text = column.label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            val formatter = rememberCompletionDateFormatter()
            EditableDate(
                text = date?.let { formatter.format(it) }
                    ?: stringResource(R.string.tracker_entry_no_date),
                date = date,
                today = today,
                onDateChange = onDateChange
            )
        }

        TrackerColumnType.NUMBER -> OutlinedTextField(
            state = field,
            textStyle = typedTextFieldStyle(),
            label = { Text(column.label) },
            inputTransformation = NumberLimit,
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        TrackerColumnType.TEXT -> OutlinedTextField(
            state = field,
            textStyle = typedTextFieldStyle(),
            label = { Text(column.label) },
            inputTransformation = TextLimit,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
    }
}

/** Delete, for a saved row, and Save. Delete asks first. */
@Composable
private fun TrackerEntryButtons(
    uiState: TrackerEntryUiState.Ready,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (uiState.canDelete) {
            TextButton(onClick = { confirmingDelete = true }) {
                Text(stringResource(R.string.tracker_entry_delete))
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                onSave()
                // Done editing: closes the keyboard.
                focusManager.clearFocus()
            },
            enabled = uiState.canSave
        ) {
            Text(stringResource(R.string.tracker_entry_save))
        }
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.tracker_entry_delete_title, uiState.rowLabel)) },
            text = { Text(stringResource(R.string.tracker_entry_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        onDelete()
                    }
                ) {
                    Text(stringResource(R.string.tracker_entry_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.tracker_entry_cancel))
                }
            }
        )
    }
}
