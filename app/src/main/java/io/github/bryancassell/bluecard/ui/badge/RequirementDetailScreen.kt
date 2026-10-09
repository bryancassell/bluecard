package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.foundation.ScrollState
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.data.progress.NOTES_MAX_LENGTH
import io.github.bryancassell.bluecard.data.progress.SIGNED_OFF_BY_MAX_LENGTH
import io.github.bryancassell.bluecard.data.progress.TimeInRank
import io.github.bryancassell.bluecard.ui.ConfirmDiscardOnBack
import io.github.bryancassell.bluecard.ui.KeepInViewWhileFocused
import io.github.bryancassell.bluecard.ui.ScreenLoadingIndicator
import io.github.bryancassell.bluecard.ui.ScreenMessage
import io.github.bryancassell.bluecard.ui.TaskFailure
import io.github.bryancassell.bluecard.ui.TaskFailureSnackbarHost
import io.github.bryancassell.bluecard.ui.TextLengthLimit
import io.github.bryancassell.bluecard.ui.singleLineInput
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
        signedOffBy = viewModel.signedOffBy,
        comment = viewModel.comment,
        onOpenRequirement = onOpenRequirement,
        onOpenTrackerEntry = onOpenTrackerEntry,
        onOpenBadge = onOpenBadge,
        onCompletedChange = viewModel::setCompleted,
        onCompletedDateChange = viewModel::setCompletedDate,
        today = viewModel::today,
        onSave = viewModel::save,
        onClear = viewModel::clear,
        onDiscard = onClose,
        onSaveFailureShown = viewModel::onSaveFailureShown,
        modifier = modifier
    )
}

/**
 * A requirement's own page: whether it's complete, and when for one the scout marks complete or
 * one complete once its tracker's rows are, the same for any own work it asks for besides its
 * sub-requirements or rows, its sub-requirements, its tracker, and the scout's [comment] on
 * it, below who it was [signedOffBy] for a rank's requirement. A sub-requirement opens its own
 * page in turn, and a tracker row opens the Tracker entry page ([onOpenTrackerEntry]) with its
 * entry's ID, if it has one, and its number. Adding a row to a log opens it with neither. A
 * rank's requirement that asks for merit badges lists the badges the scout has completed, each
 * of which opens its page ([onOpenBadge]). At the bottom, once anything is recorded, the scout
 * can clear it ([onClear]), with what's recorded for the requirements under it. Back with
 * unsaved changes to the sign-off or comment asks first, then [onDiscard] closes the page without
 * saving them.
 */
@Composable
fun RequirementDetailScreen(
    uiState: RequirementDetailUiState,
    signedOffBy: TextFieldState,
    comment: TextFieldState,
    onOpenRequirement: (number: String) -> Unit,
    onOpenTrackerEntry: (entryId: Long?, rowNumber: Int?) -> Unit,
    onOpenBadge: (badgeId: String) -> Unit,
    onCompletedChange: (Boolean) -> Unit,
    onCompletedDateChange: (LocalDate?) -> Unit,
    today: () -> LocalDate,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onDiscard: () -> Unit,
    onSaveFailureShown: (TaskFailure) -> Unit,
    modifier: Modifier = Modifier
) {
    val ready = uiState as? RequirementDetailUiState.Ready
    ConfirmDiscardOnBack(
        changed = ready?.textChanged == true,
        onDiscard = onDiscard,
        // Says it's the text that isn't saved, as the checkbox and date save straight away.
        message = stringResource(
            if (ready?.hasSignOffField == true) {
                R.string.discard_changes_message_sign_off_and_notes
            } else {
                R.string.discard_changes_message_notes
            }
        )
    )
    when (uiState) {
        RequirementDetailUiState.Loading -> ScreenLoadingIndicator(modifier)

        RequirementDetailUiState.LoadFailed ->
            ScreenMessage(stringResource(R.string.load_failed), modifier)

        RequirementDetailUiState.Unavailable ->
            ScreenMessage(stringResource(R.string.requirement_detail_unavailable), modifier)

        // Ends the page above the keyboard, so the text fields can be scrolled into view.
        is RequirementDetailUiState.Ready -> Box(modifier = modifier.imePadding()) {
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                RequirementHeader(uiState.advancementName, uiState.requirement, uiState.timeInRank)
                val requirement = uiState.requirement
                // Its own work is stored as the requirement's own progress, like one marked by
                // hand, so it has the same checkbox and date. Only a requirement with own work
                // gets one: a checkbox on every requirement with sub-requirements would add a trip
                // to the many that only group them. It's labeled with our summary of the work, so
                // it says exactly what to check off, where "Completed" would be wrong until the
                // sub-requirements are done too (#143).
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
                    // Under the header's "Completed", where a checkbox's date would be. Once the
                    // date is removed, Add date opens at the date the last row was first saved,
                    // which gives it a way back without another button: Clear would delete the
                    // rows too.
                    CompletionDate(
                        uiState.completedDate,
                        today,
                        onCompletedDateChange,
                        suggested = uiState.rowsCompletedDate
                    )
                }
                // Right above the sub-requirements it counts, below the checkbox for its own work.
                // Under the summary, it had the checkbox between it and what it counts.
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
                TextFields(
                    signedOffBy = signedOffBy.takeIf { uiState.hasSignOffField },
                    comment = comment,
                    changed = uiState.textChanged,
                    onSave = onSave,
                    scrollState = scrollState
                )
                if (uiState.canClear) {
                    ClearRequirement(
                        number = requirement.number,
                        hasChildren = uiState.children.isNotEmpty(),
                        unsavedText = uiState.textChanged,
                        hasSignOffField = uiState.hasSignOffField,
                        unearnedRanks = uiState.unearnedByClear,
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
        // Only here, not on the requirement's row, and still once it's checked off.
        timeInRank?.let {
            Text(
                text = timeInRankText(it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // One the scout marks by hand has a "Completed" checkbox instead. One with own work
        // shows it here, as its checkbox is only for that work.
        //
        // Not a live region, so screen readers aren't told when it appears or goes without the
        // scout ticking anything: by its fixed-row tracker's last row being saved or a row of a
        // full tracker deleted, or by its sub-requirements, own work or merit badges. TalkBack
        // reads it here and as the row's state as the scout reaches them, as a sighted scout
        // sees it on going back. Android 16's behavior changes ask that live regions be "used
        // sparingly" (#109).
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

/**
 * Who [signedOffBy] on the requirement, unless it's null, as for a badge's requirement, and the
 * scout's comment on it, saved together when they choose. One Save keeps the page to one button,
 * and its single write can't save one field and fail the other (#248). While the comment has
 * focus, it and Save are kept in view in the page's [scrollState], as a tracker row's last field
 * and Save are: Save sat partly behind the keyboard on a phone (#178). Clear progress, under
 * Save, can stay behind the keyboard, as it isn't part of saving what the scout types. The
 * sign-off keeps only its line in view, like a tracker row's earlier fields.
 */
@Composable
private fun TextFields(
    signedOffBy: TextFieldState?,
    comment: TextFieldState,
    changed: Boolean,
    onSave: () -> Unit,
    scrollState: ScrollState
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        signedOffBy?.let { SignedOffByField(it) }
        // Keeps Save above the keyboard while the scout types in the comment.
        KeepInViewWhileFocused(scrollState) {
            OutlinedTextField(
                state = comment,
                textStyle = typedTextFieldStyle(),
                label = { Text(stringResource(R.string.requirement_comment)) },
                inputTransformation = CommentLengthLimit,
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    onSave()
                    // Done editing: closes the keyboard.
                    focusManager.clearFocus()
                },
                enabled = changed,
                // Here, not on the Column, so the page's margin under Save comes into view with it.
                modifier = Modifier.align(Alignment.End).padding(bottom = 16.dp)
            ) {
                // With the sign-off, it saves more than the notes.
                val label = if (signedOffBy != null) {
                    R.string.requirement_save
                } else {
                    R.string.requirement_save_comment
                }
                Text(stringResource(label))
            }
        }
    }
}

private val SignedOffByInput = singleLineInput(maxLength = SIGNED_OFF_BY_MAX_LENGTH)

/** A one-line field for who signed off on a rank's requirement, such as the Scoutmaster. */
@Composable
private fun SignedOffByField(state: TextFieldState) {
    OutlinedTextField(
        state = state,
        textStyle = typedTextFieldStyle(),
        label = { Text(stringResource(R.string.requirement_signed_off_by)) },
        inputTransformation = SignedOffByInput,
        lineLimits = TextFieldLineLimits.SingleLine,
        // Next moves on to the notes.
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Next
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Clears what the scout recorded for the requirement, and for those under it if it
 * [hasChildren], once they confirm. The dialog warns that [unsavedText], changes to its text
 * fields not saved yet, go too: the sign-off and notes if it [hasSignOffField], or else the notes.
 * It names the ranks clearing would un-earn ([unearnedRanks]).
 */
@Composable
private fun ClearRequirement(
    number: String,
    hasChildren: Boolean,
    unsavedText: Boolean,
    hasSignOffField: Boolean,
    unearnedRanks: List<String>,
    onClear: () -> Unit
) {
    ClearProgress(
        title = stringResource(R.string.requirement_clear_title, number),
        message = stringResource(
            when {
                hasChildren && unsavedText && hasSignOffField ->
                    R.string.requirement_clear_message_with_children_and_unsaved_sign_off_and_notes

                hasChildren && unsavedText ->
                    R.string.requirement_clear_message_with_children_and_unsaved_notes

                hasChildren -> R.string.requirement_clear_message_with_children

                unsavedText && hasSignOffField ->
                    R.string.requirement_clear_message_with_unsaved_sign_off_and_notes

                unsavedText -> R.string.requirement_clear_message_with_unsaved_notes

                else -> R.string.requirement_clear_message
            }
        ),
        unearnedRanks = unearnedRanks,
        onClear = onClear
    )
}
