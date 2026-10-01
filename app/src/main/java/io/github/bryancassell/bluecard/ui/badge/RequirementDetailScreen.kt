package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.ui.ConfirmDialog
import io.github.bryancassell.bluecard.ui.LoadFailedMessage
import io.github.bryancassell.bluecard.ui.SaveFailedSnackbarHost
import io.github.bryancassell.bluecard.ui.SaveFailure
import io.github.bryancassell.bluecard.ui.ScreenMessage
import io.github.bryancassell.bluecard.ui.TextLengthLimit
import io.github.bryancassell.bluecard.ui.removalButtonColors
import io.github.bryancassell.bluecard.ui.typedTextFieldStyle
import java.time.LocalDate

/** Connects the Requirement detail screen to its ViewModel. */
@Composable
fun RequirementDetailRoute(
    badgeId: String,
    number: String,
    onOpenRequirement: (number: String) -> Unit,
    onOpenTrackerEntry: (entryId: Long?, rowNumber: Int?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RequirementDetailViewModel =
        hiltViewModel<RequirementDetailViewModel, RequirementDetailViewModel.Factory> {
            it.create(badgeId, number)
        }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RequirementDetailScreen(
        uiState = uiState,
        comment = viewModel.comment,
        onOpenRequirement = onOpenRequirement,
        onOpenTrackerEntry = onOpenTrackerEntry,
        onCompletedChange = viewModel::setCompleted,
        onCompletedDateChange = viewModel::setCompletedDate,
        onSaveComment = viewModel::saveComment,
        onClear = viewModel::clear,
        onSaveFailureShown = viewModel::onSaveFailureShown,
        modifier = modifier
    )
}

/**
 * A requirement's own page: whether it's complete, and when for one the scout marks complete,
 * its sub-requirements, its tracker, and the scout's [comment] on it. A sub-requirement opens
 * its own page in turn, and a tracker row opens the Tracker entry page
 * ([onOpenTrackerEntry]) with its entry's ID, if it has one, and its number. Adding a row to a
 * log opens it with neither. At the bottom, once anything is recorded, the scout can clear it
 * ([onClear]), with what's recorded for the requirements under it.
 */
@Composable
fun RequirementDetailScreen(
    uiState: RequirementDetailUiState,
    comment: TextFieldState,
    onOpenRequirement: (number: String) -> Unit,
    onOpenTrackerEntry: (entryId: Long?, rowNumber: Int?) -> Unit,
    onCompletedChange: (Boolean) -> Unit,
    onCompletedDateChange: (LocalDate?) -> Unit,
    onSaveComment: () -> Unit,
    onClear: () -> Unit,
    onSaveFailureShown: (SaveFailure) -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        RequirementDetailUiState.Loading -> LoadingIndicator(modifier)

        RequirementDetailUiState.LoadFailed -> LoadFailedMessage(modifier)

        RequirementDetailUiState.Unavailable ->
            ScreenMessage(stringResource(R.string.requirement_detail_unavailable), modifier)

        // Ends the page above the keyboard, so the comment field can be scrolled into view.
        is RequirementDetailUiState.Ready -> Box(modifier = modifier.imePadding()) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                RequirementHeader(uiState.badgeName, uiState.requirement)
                val requirement = uiState.requirement
                if (requirement.markedByHand) {
                    CompletedCheckbox(requirement.completed, onCompletedChange)
                    if (requirement.completed) {
                        CompletionDate(uiState.completedDate, uiState.today, onCompletedDateChange)
                    }
                }
                uiState.children.forEach {
                    RequirementRow(item = it, onOpen = onOpenRequirement)
                }
                uiState.tracker?.let { tracker ->
                    TrackerSection(
                        tracker = tracker,
                        onOpenRow = { onOpenTrackerEntry(it.entryId, it.number) },
                        onAddRow = { onOpenTrackerEntry(null, null) },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                CommentField(comment, uiState.commentChanged, onSaveComment)
                if (uiState.canClear) {
                    ClearProgress(
                        number = requirement.number,
                        hasChildren = uiState.children.isNotEmpty(),
                        unsavedNotes = uiState.commentChanged,
                        onClear = onClear
                    )
                }
            }
            SaveFailedSnackbarHost(
                failure = uiState.saveFailure,
                onShown = onSaveFailureShown,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun RequirementHeader(badgeName: String, requirement: RequirementItem) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = badgeName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.requirement_detail_title, requirement.number),
            style = MaterialTheme.typography.headlineMedium,
            // Lets screen reader users jump to it.
            modifier = Modifier.semantics { heading() }
        )
        Text(text = requirement.summary, style = MaterialTheme.typography.bodyLarge)
        requirement.choice?.let {
            Text(
                text = choiceLabel(it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // One the scout marks by hand has a checkbox instead.
        if (!requirement.markedByHand && requirement.completed) {
            Text(
                text = stringResource(R.string.requirement_completed),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        // As on its row. One the scout marks by hand keeps its checkbox, for when they did it
        // anyway.
        if (requirement.notNeeded) {
            Text(
                text = stringResource(R.string.requirement_not_needed),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The whole row toggles the checkbox, and screen readers read it as one checkbox. */
@Composable
private fun CompletedCheckbox(completed: Boolean, onCompletedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = completed, role = Role.Checkbox, onValueChange = onCompletedChange)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp)
    ) {
        Checkbox(checked = completed, onCheckedChange = null)
        Text(
            text = stringResource(R.string.requirement_completed),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

/** The date a completed requirement was completed on, which the scout can change or remove. */
@Composable
private fun CompletionDate(date: LocalDate?, today: LocalDate, onDateChange: (LocalDate?) -> Unit) {
    val formatter = rememberCompletionDateFormatter()
    EditableDate(
        text = if (date == null) {
            stringResource(R.string.requirement_no_date)
        } else {
            stringResource(R.string.requirement_completed_on, formatter.format(date))
        },
        date = date,
        today = today,
        onDateChange = onDateChange
    )
}

/**
 * Plenty for notes on a requirement. The field's text is saved with the screen's state, which
 * has a size limit, so a huge paste mustn't reach it.
 */
private val CommentLengthLimit = TextLengthLimit(maxLength = 2_000)

/** The scout's comment on the requirement, saved when they choose. */
@Composable
private fun CommentField(comment: TextFieldState, changed: Boolean, onSave: () -> Unit) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            state = comment,
            textStyle = typedTextFieldStyle(),
            label = { Text(stringResource(R.string.requirement_comment)) },
            inputTransformation = CommentLengthLimit,
            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                onSave()
                // Done editing: closes the keyboard.
                focusManager.clearFocus()
            },
            enabled = changed
        ) {
            Text(stringResource(R.string.requirement_save_comment))
        }
    }
}

/**
 * Clears what the scout recorded for the requirement, and for those under it if it
 * [hasChildren], once they confirm. The dialog warns that [unsavedNotes], changes to the notes
 * not saved yet, go too. Red, and last on the page, so it isn't tapped by mistake.
 */
@Composable
private fun ClearProgress(
    number: String,
    hasChildren: Boolean,
    unsavedNotes: Boolean,
    onClear: () -> Unit
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    TextButton(
        onClick = { confirming = true },
        colors = removalButtonColors(),
        // Lines the button's text up with the page's, as for Add counselor.
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 16.dp)
    ) {
        Text(stringResource(R.string.requirement_clear))
    }
    if (confirming) {
        ConfirmDialog(
            title = stringResource(R.string.requirement_clear_title, number),
            message = stringResource(
                when {
                    hasChildren && unsavedNotes ->
                        R.string.requirement_clear_message_with_children_and_unsaved_notes

                    hasChildren -> R.string.requirement_clear_message_with_children

                    unsavedNotes -> R.string.requirement_clear_message_with_unsaved_notes

                    else -> R.string.requirement_clear_message
                }
            ),
            confirmLabel = stringResource(R.string.requirement_clear_confirm),
            onConfirm = {
                confirming = false
                onClear()
            },
            onDismiss = { confirming = false }
        )
    }
}
