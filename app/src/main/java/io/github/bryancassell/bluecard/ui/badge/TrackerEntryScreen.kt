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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.progress.TRACKER_MULTILINE_TEXT_MAX_LENGTH
import io.github.bryancassell.bluecard.data.progress.TRACKER_NUMBER_MAX_LENGTH
import io.github.bryancassell.bluecard.data.progress.TRACKER_TEXT_MAX_LENGTH
import io.github.bryancassell.bluecard.ui.ConfirmDialog
import io.github.bryancassell.bluecard.ui.ConfirmDiscardOnBack
import io.github.bryancassell.bluecard.ui.KeepInViewWhileFocused
import io.github.bryancassell.bluecard.ui.LoadingOrMessage
import io.github.bryancassell.bluecard.ui.NumberInput
import io.github.bryancassell.bluecard.ui.TaskFailure
import io.github.bryancassell.bluecard.ui.TaskFailureSnackbarHost
import io.github.bryancassell.bluecard.ui.TextLengthLimit
import io.github.bryancassell.bluecard.ui.removalButtonColors
import io.github.bryancassell.bluecard.ui.singleLineInput
import io.github.bryancassell.bluecard.ui.typedTextFieldStyle
import java.time.LocalDate

/** Connects the Tracker entry screen to its ViewModel. */
@Composable
fun TrackerEntryRoute(
    advancementId: String,
    number: String,
    entryId: Long?,
    rowNumber: Int?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrackerEntryViewModel =
        hiltViewModel<TrackerEntryViewModel, TrackerEntryViewModel.Factory> {
            it.create(advancementId, number, entryId, rowNumber)
        }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TrackerEntryScreen(
        uiState = uiState,
        fields = viewModel.fields,
        onDateChange = viewModel::setDate,
        today = viewModel::today,
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
 * page ([onClose]) once they're done; Delete asks first. Back with unsaved changes asks too,
 * then closes it without saving them.
 */
@Composable
fun TrackerEntryScreen(
    uiState: TrackerEntryUiState,
    fields: Map<String, TextFieldState>,
    onDateChange: (columnId: String, date: LocalDate?) -> Unit,
    today: () -> LocalDate,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    onSaveFailureShown: (TaskFailure) -> Unit,
    modifier: Modifier = Modifier
) {
    ConfirmDiscardOnBack(
        changed = (uiState as? TrackerEntryUiState.Ready)?.changed == true,
        onDiscard = onClose
    )
    when (uiState) {
        // One branch, so screen readers hear the message (see LoadingOrMessage).
        TrackerEntryUiState.Loading,
        TrackerEntryUiState.LoadFailed,
        TrackerEntryUiState.Unavailable -> LoadingOrMessage(
            message = when (uiState) {
                TrackerEntryUiState.LoadFailed -> stringResource(R.string.load_failed)

                TrackerEntryUiState.Unavailable ->
                    stringResource(R.string.tracker_entry_unavailable)

                else -> null
            },
            modifier = modifier
        )

        is TrackerEntryUiState.Ready -> {
            if (uiState.done) {
                val currentOnClose by rememberUpdatedState(onClose)
                LaunchedEffect(Unit) { currentOnClose() }
            }
            val scrollState = rememberScrollState()
            // Ends the page above the keyboard, so every field can be scrolled into view.
            Box(modifier = modifier.imePadding()) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TrackerEntryHeader(uiState)
                    val lastTextColumnId =
                        uiState.columns.lastOrNull { it.type != TrackerColumnType.DATE }?.id
                    val trackerField: @Composable (TrackerColumn) -> Unit = { column ->
                        TrackerField(
                            column = column,
                            field = fields.getValue(column.id),
                            date = uiState.dates[column.id],
                            today = today,
                            onDateChange = { onDateChange(column.id, it) },
                            lastTextField = column.id == lastTextColumnId
                        )
                    }
                    uiState.columns.dropLast(1).forEach { column ->
                        // Keeps each date field's picker with its column.
                        key(column.id) { trackerField(column) }
                    }
                    // Keeps Save above the keyboard while the scout types in the last field.
                    KeepInViewWhileFocused(scrollState) {
                        uiState.columns.lastOrNull()?.let { trackerField(it) }
                        TrackerEntryButtons(uiState, onSave, onDelete)
                    }
                }
                TaskFailureSnackbarHost(
                    failure = uiState.saveFailure,
                    message = stringResource(R.string.save_failed),
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
            text = uiState.advancementName,
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

/** A text column's field is single-line, so a pasted line break becomes a space. */
private val TextLimit = singleLineInput(maxLength = TRACKER_TEXT_MAX_LENGTH)

private val MultilineTextLimit = TextLengthLimit(maxLength = TRACKER_MULTILINE_TEXT_MAX_LENGTH)

private val NumberLimit = NumberInput.then(TextLengthLimit(maxLength = TRACKER_NUMBER_MAX_LENGTH))

/**
 * The field for one column: a date with a picker, or a text field for text or a number. A
 * multi-line text field is drawn like the requirement notes field, and its keyboard keeps Enter
 * for a new line. A one-line field's keyboard has Next, or Done on the [lastTextField]. Next
 * moves to the next text field, past a date's buttons, which can't take focus in touch mode.
 */
@Composable
private fun TrackerField(
    column: TrackerColumn,
    field: TextFieldState,
    date: LocalDate?,
    today: () -> LocalDate,
    onDateChange: (LocalDate?) -> Unit,
    lastTextField: Boolean
) {
    val oneLineImeAction = if (lastTextField) ImeAction.Done else ImeAction.Next
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
                onDateChange = onDateChange,
                label = column.label
            )
        }

        TrackerColumnType.NUMBER -> OutlinedTextField(
            state = field,
            textStyle = typedTextFieldStyle(),
            label = { Text(column.label) },
            inputTransformation = NumberLimit,
            // Merged with NumberInput's options, so the keyboard stays decimal.
            keyboardOptions = KeyboardOptions(imeAction = oneLineImeAction),
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        TrackerColumnType.TEXT, TrackerColumnType.MULTILINE_TEXT -> {
            val multiline = column.type == TrackerColumnType.MULTILINE_TEXT
            OutlinedTextField(
                state = field,
                textStyle = typedTextFieldStyle(),
                label = { Text(column.label) },
                inputTransformation = if (multiline) MultilineTextLimit else TextLimit,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = if (multiline) ImeAction.Default else oneLineImeAction
                ),
                lineLimits = if (multiline) {
                    TextFieldLineLimits.MultiLine(minHeightInLines = 3)
                } else {
                    TextFieldLineLimits.SingleLine
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }
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
        if (uiState.hasSavedEntry) {
            TextButton(
                onClick = { confirmingDelete = true },
                enabled = uiState.canDelete,
                colors = removalButtonColors()
            ) {
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
        ConfirmDialog(
            title = stringResource(R.string.tracker_entry_delete_title, uiState.rowLabel),
            message = stringResource(R.string.tracker_entry_delete_message),
            confirmLabel = stringResource(R.string.tracker_entry_delete),
            onConfirm = {
                confirmingDelete = false
                onDelete()
            },
            onDismiss = { confirmingDelete = false }
        )
    }
}
