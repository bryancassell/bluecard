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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.data.progress.NOTES_MAX_LENGTH
import io.github.bryancassell.bluecard.data.progress.TimeInRank
import io.github.bryancassell.bluecard.ui.ConfirmDiscardOnBack
import io.github.bryancassell.bluecard.ui.LoadingOrMessage
import io.github.bryancassell.bluecard.ui.TaskFailure
import io.github.bryancassell.bluecard.ui.TaskFailureSnackbarHost
import io.github.bryancassell.bluecard.ui.TextLengthLimit
import io.github.bryancassell.bluecard.ui.typedTextFieldStyle
import java.time.LocalDate

/** Connects the Requirement detail screen to its ViewModel. */
@Composable
fun RequirementDetailRoute(
    advancementId: String,
    number: String,
    onOpenRequirement: (number: String) -> Unit,
    onOpenTrackerEntry: (entryId: Long?, rowNumber: Int?) -> Unit,
    onOpenBadge: (badgeId: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RequirementDetailViewModel =
        hiltViewModel<RequirementDetailViewModel, RequirementDetailViewModel.Factory> {
            it.create(advancementId, number)
        }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RequirementDetailScreen(
        uiState = uiState,
        comment = viewModel.comment,
        onOpenRequirement = onOpenRequirement,
        onOpenTrackerEntry = onOpenTrackerEntry,
        onOpenBadge = onOpenBadge,
        onCompletedChange = viewModel::setCompleted,
        onCompletedDateChange = viewModel::setCompletedDate,
        today = viewModel::today,
        onSaveComment = viewModel::saveComment,
        onClear = viewModel::clear,
        onDiscard = onClose,
        onSaveFailureShown = viewModel::onSaveFailureShown,
        modifier = modifier
    )
}

/**
 * A requirement's own page: whether it's complete, and when for one the scout marks complete or
 * one complete once its tracker's rows are, the same for its own work if it asks for some
 * besides its sub-requirements, its sub-requirements, its tracker, and the scout's [comment] on
 * it. A sub-requirement opens its own page in turn, and a tracker row opens the Tracker entry
 * page ([onOpenTrackerEntry]) with its entry's ID, if it has one, and its number. Adding a row to
 * a log opens it with neither. A rank's requirement that asks for merit badges lists the badges
 * the scout has completed, each of which opens its page ([onOpenBadge]). At the bottom, once
 * anything is recorded, the scout can clear it ([onClear]), with what's recorded for the
 * requirements under it. Back with unsaved changes to the comment asks first, then [onDiscard]
 * closes the page without saving them.
 */
@Composable
fun RequirementDetailScreen(
    uiState: RequirementDetailUiState,
    comment: TextFieldState,
    onOpenRequirement: (number: String) -> Unit,
    onOpenTrackerEntry: (entryId: Long?, rowNumber: Int?) -> Unit,
    onOpenBadge: (badgeId: String) -> Unit,
    onCompletedChange: (Boolean) -> Unit,
    onCompletedDateChange: (LocalDate?) -> Unit,
    today: () -> LocalDate,
    onSaveComment: () -> Unit,
    onClear: () -> Unit,
    onDiscard: () -> Unit,
    onSaveFailureShown: (TaskFailure) -> Unit,
    modifier: Modifier = Modifier
) {
    ConfirmDiscardOnBack(
        changed = (uiState as? RequirementDetailUiState.Ready)?.commentChanged == true,
        onDiscard = onDiscard,
        message = stringResource(R.string.discard_changes_message_notes)
    )
    when (uiState) {
        // One branch, so screen readers hear the message (see LoadingOrMessage).
        RequirementDetailUiState.Loading,
        RequirementDetailUiState.LoadFailed,
        RequirementDetailUiState.Unavailable -> LoadingOrMessage(
            message = when (uiState) {
                RequirementDetailUiState.LoadFailed -> stringResource(R.string.load_failed)

                RequirementDetailUiState.Unavailable ->
                    stringResource(R.string.requirement_detail_unavailable)

                else -> null
            },
            modifier = modifier
        )

        // Ends the page above the keyboard, so the comment field can be scrolled into view.
        is RequirementDetailUiState.Ready -> Box(modifier = modifier.imePadding()) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                RequirementHeader(uiState.advancementName, uiState.requirement, uiState.timeInRank)
                val requirement = uiState.requirement
                // Its own work is stored as the requirement's own progress, like one marked by
                // hand, so it has the same checkbox and date.
                val ownWork = requirement.ownWork
                if (requirement.markedByHand || ownWork != null) {
                    val checked = ownWork?.completed ?: requirement.completed
                    CompletedCheckbox(
                        label = ownWork?.summary ?: stringResource(R.string.requirement_completed),
                        checked = checked,
                        onCheckedChange = onCompletedChange
                    )
                    if (checked) {
                        CompletionDate(uiState.completedDate, today, onCompletedDateChange)
                    }
                } else if (requirement.completesFromRows && requirement.completed) {
                    // Under the header's "Completed", where a checkbox's date would be.
                    CompletionDate(
                        uiState.completedDate,
                        today,
                        onCompletedDateChange,
                        suggested = uiState.rowsCompletedDate
                    )
                }
                // Right above the sub-requirements it counts, below the checkbox for its own work.
                requirement.choice?.let {
                    Text(
                        text = choiceLabel(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp)
                    )
                }
                RequirementRows(items = uiState.children, onOpen = onOpenRequirement)
                uiState.tracker?.let { tracker ->
                    TrackerSection(
                        tracker = tracker,
                        onOpenRow = { onOpenTrackerEntry(it.entryId, it.number) },
                        onAddRow = { onOpenTrackerEntry(null, null) },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                requirement.meritBadges?.let { credit ->
                    MeritBadgeSection(
                        credit = credit,
                        badges = uiState.earnedBadges.orEmpty(),
                        onOpenBadge = onOpenBadge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                CommentField(comment, uiState.commentChanged, onSaveComment)
                if (uiState.canClear) {
                    ClearRequirement(
                        number = requirement.number,
                        hasChildren = uiState.children.isNotEmpty(),
                        unsavedNotes = uiState.commentChanged,
                        onClear = onClear
                    )
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

@Composable
private fun RequirementHeader(
    advancementName: String,
    requirement: RequirementItem,
    timeInRank: TimeInRank?
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = advancementName,
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
        timeInRank?.let {
            Text(
                text = timeInRankText(it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // One the scout marks by hand has a "Completed" checkbox instead. One with own work
        // shows it here, as its checkbox is only for that work.
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

/**
 * When the scout becomes eligible for a requirement that asks for time in the rank below, or why
 * that isn't known yet.
 */
@Composable
private fun timeInRankText(timeInRank: TimeInRank): String {
    val months = timeInRank.months
    val rankBelow = timeInRank.rankBelow
    val withoutDate = when (val eligibility = timeInRank.eligibility) {
        is TimeInRank.Eligibility.From -> return pluralStringResource(
            R.plurals.requirement_time_in_rank_eligible_from,
            months,
            rememberCompletionDateFormatter().format(eligibility.date),
            months,
            rankBelow
        )

        TimeInRank.Eligibility.RankBelowHasNoDate -> R.plurals.requirement_time_in_rank_no_date

        TimeInRank.Eligibility.RankBelowNotEarned -> R.plurals.requirement_time_in_rank_not_earned
    }
    return pluralStringResource(withoutDate, months, months, rankBelow)
}

/**
 * The whole row toggles the checkbox, and screen readers read it as one checkbox. A long
 * [label], such as a summary of a requirement's own work, wraps.
 */
@Composable
private fun CompletedCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
        )
    }
}

/**
 * The date a completed requirement was completed on, which the scout can change or remove.
 * Without one, Add date opens the picker at [suggested], if given, or else at today.
 */
@Composable
private fun CompletionDate(
    date: LocalDate?,
    today: () -> LocalDate,
    onDateChange: (LocalDate?) -> Unit,
    suggested: LocalDate? = null
) {
    val formatter = rememberCompletionDateFormatter()
    EditableDate(
        text = if (date == null) {
            stringResource(R.string.requirement_no_date)
        } else {
            stringResource(R.string.requirement_completed_on, formatter.format(date))
        },
        date = date,
        today = today,
        onDateChange = onDateChange,
        suggested = suggested
    )
}

private val CommentLengthLimit = TextLengthLimit(maxLength = NOTES_MAX_LENGTH)

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
 * not saved yet, go too.
 */
@Composable
private fun ClearRequirement(
    number: String,
    hasChildren: Boolean,
    unsavedNotes: Boolean,
    onClear: () -> Unit
) {
    ClearProgress(
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
        onClear = onClear
    )
}
