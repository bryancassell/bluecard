package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Rank
import io.github.bryancassell.bluecard.data.catalog.getAdvancements
import io.github.bryancassell.bluecard.data.progress.EarnedBadge
import io.github.bryancassell.bluecard.data.progress.EarnedBadges
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.TimeInRank
import io.github.bryancassell.bluecard.data.progress.badgeStart
import io.github.bryancassell.bluecard.data.progress.completesFromRows
import io.github.bryancassell.bluecard.data.progress.completion
import io.github.bryancassell.bluecard.data.progress.earnedBadges
import io.github.bryancassell.bluecard.data.progress.normalizedText
import io.github.bryancassell.bluecard.data.progress.rowsCompletedDate
import io.github.bryancassell.bluecard.data.progress.standings
import io.github.bryancassell.bluecard.data.progress.timeInRank
import io.github.bryancassell.bluecard.ui.StoredTextFields
import io.github.bryancassell.bluecard.ui.TaskFailure
import io.github.bryancassell.bluecard.ui.TaskRunner
import io.github.bryancassell.bluecard.ui.catchLoadFailure
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * One requirement of a badge or rank and its sub-requirements, with the scout's progress:
 * whether it's complete and when, who signed off on it, for a rank's, their comment on it, and its
 * tracker.
 */
@HiltViewModel(assistedFactory = RequirementDetailViewModel.Factory::class)
class RequirementDetailViewModel @AssistedInject constructor(
    @Assisted("advancementId") private val advancementId: String,
    @Assisted("number") private val number: String,
    private val catalogRepository: CatalogRepository,
    private val progressRepository: ProgressRepository,
    private val clock: Clock,
    // Keeps an unsaved sign-off and comment, and the date to bring back to an unchecked
    // requirement, if the system stops the app in the background.
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val fields = StoredTextFields(savedStateHandle, SIGNED_OFF_BY, COMMENT)

    /**
     * The text of the field for who signed off on the requirement, which only a rank's
     * requirement shows. It starts as [comment] does.
     */
    val signedOffBy = fields[SIGNED_OFF_BY]

    /**
     * The comment field's text, which the field edits directly. It starts as the saved comment
     * once that loads, or as the unsaved comment the system stopped the app with.
     */
    val comment = fields[COMMENT]

    private val saves = TaskRunner(viewModelScope)
    private val recorder = CompletionRecorder(
        advancementId,
        number,
        catalogRepository,
        progressRepository,
        clock,
        savedStateHandle
    )

    /**
     * The requirement as recorded, or null if the badge's or rank's requirements don't have it.
     * It's built when the catalog or progress changes, not on each keystroke in its fields.
     */
    private val recorded = flow {
        val catalog = catalogRepository.getAdvancements()
        val ranks = catalog.filterIsInstance<Rank>()
        val badges = catalog.filterIsInstance<MeritBadge>()
        // A rank's page reads every rank's progress, because a rank above it can count it as
        // earned, and its time in rank counts from the rank below. It reads every badge's too,
        // which its requirements that ask for merit badges count. A badge's reads only its own.
        val isRank = ranks.any { it.id == advancementId }
        val progress = if (isRank) {
            progressRepository.observeAllProgress()
        } else {
            progressRepository.observeProgress(advancementId).map { listOfNotNull(it) }
        }
        emitAll(
            progress.map { all ->
                val byId = all.associateBy { it.badge.badgeId }
                val earnedBadges = if (isRank) badges.earnedBadges(byId) else EarnedBadges.None
                val standings = if (isRank) ranks.standings(byId, earnedBadges) else null
                // Checked on every change, not only when the page opens, because which version
                // the badge or rank uses depends on its progress.
                val found = catalog.advancementRequirementsAmong(
                    advancementId,
                    byId,
                    earnedBadges,
                    standings
                )
                found to standings
            }
        )
    }.map { (found, standings) ->
        found?.version?.find(number)?.let { requirement ->
            val recorded = found.recorded[number]
            val numbersWithin = requirement.numbersWithin()
            RecordedRequirement(
                advancementName = found.advancement.name,
                requirement = found.item(requirement),
                // For one complete from its rows or from badges, the date it was completed on:
                // for the former, the one the scout gave, if any.
                completedDate = if (requirement.completesFromRows ||
                    requirement.meritBadges != null
                ) {
                    requirement.completion(found.recorded, found.trackerEntries, found.earnedBadges)
                        ?.date
                } else {
                    recorded?.completedDate
                },
                rowsCompletedDate = requirement.rowsCompletedDate(found.trackerEntries),
                children = requirement.children.map(found::item),
                tracker = requirement.tracker?.toItem(found.trackerEntries[number].orEmpty()),
                timeInRank = standings?.timeInRank(advancementId, requirement),
                earnedBadges = requirement.meritBadges?.let { found.earnedBadges.inNameOrder() },
                hasSignOffField = found.advancement is Rank,
                signedOffBy = recorded?.signedOffBy,
                comment = recorded?.comment,
                numbersWithin = numbersWithin,
                hasRecorded = found.hasRecorded(numbersWithin)
            )
        }
    }

    private class RecordedRequirement(
        val advancementName: String,
        val requirement: RequirementItem,
        val completedDate: LocalDate?,
        val rowsCompletedDate: LocalDate?,
        val children: List<RequirementItem>,
        val tracker: TrackerItem?,
        val timeInRank: TimeInRank?,
        val earnedBadges: List<EarnedBadge>?,
        /** Whether it has a field for who signed off on it, as a rank's requirement does. */
        val hasSignOffField: Boolean,
        /** Who signed off on it, as saved. */
        val signedOffBy: String?,
        /** The saved comment. */
        val comment: String?,
        /** The numbers of this requirement and of every one under it. */
        val numbersWithin: List<String>,
        /** Whether anything is recorded for them, for the scout to clear. */
        val hasRecorded: Boolean
    )

    /**
     * The requirement as the page last showed it, or null before it has. [clear] reads it, so
     * it queues its write as soon as it's called, in order with the page's other changes.
     */
    private var shown: RecordedRequirement? = null

    val uiState: StateFlow<RequirementDetailUiState> = combine(
        recorded,
        // Works the state out again as the scout types.
        snapshotFlow { signedOffBy.text.toString() to comment.text.toString() },
        saves.failure
    ) { recorded, _, saveFailure ->
        if (recorded == null) return@combine RequirementDetailUiState.Unavailable
        fields.loadOnce {
            mapOf(SIGNED_OFF_BY to recorded.signedOffBy, COMMENT to recorded.comment)
        }
        shown?.let {
            followSaved(signedOffBy, before = it.signedOffBy, saved = recorded.signedOffBy)
            followSaved(comment, before = it.comment, saved = recorded.comment)
        }
        shown = recorded
        RequirementDetailUiState.Ready(
            advancementName = recorded.advancementName,
            requirement = recorded.requirement,
            completedDate = recorded.completedDate,
            rowsCompletedDate = recorded.rowsCompletedDate,
            children = recorded.children,
            tracker = recorded.tracker,
            timeInRank = recorded.timeInRank,
            earnedBadges = recorded.earnedBadges,
            hasSignOffField = recorded.hasSignOffField,
            textChanged = edits(recorded).isNotEmpty(),
            canClear = recorded.hasRecorded,
            saveFailure = saveFailure
        )
    }.catchLoadFailure(RequirementDetailUiState.LoadFailed).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        RequirementDetailUiState.Loading
    )

    /**
     * Marks this requirement, one the scout marks by hand, or its own work, for one with own
     * work, completed or not.
     */
    fun setCompleted(completed: Boolean) {
        saves.launch { recorder.setCompleted(completed) }
    }

    /**
     * Changes the date this requirement, or its own work, was completed on, once marked complete;
     * null removes it. For one that [completesFromRows], the date is kept while a row is deleted
     * ([ProgressRepository.setCompletedFromRowsDate]).
     */
    fun setCompletedDate(date: LocalDate?) {
        // The date shows only once the page has.
        val rowCount = shown?.requirement?.takeIf { it.completesFromRows }?.tracker?.rowCount
        saves.launch {
            if (rowCount != null) {
                progressRepository.setCompletedFromRowsDate(advancementId, number, rowCount, date)
            } else {
                progressRepository.setRequirementCompletedDate(advancementId, number, date)
            }
        }
    }

    /**
     * Saves the sign-off field, for a rank's requirement, and the comment field, as this
     * requirement's. An empty one removes it.
     */
    fun save() {
        // The button shows only once the page has.
        val signOffText = signedOffBy.text.toString().takeIf { shown?.hasSignOffField == true }
        val commentText = comment.text.toString()
        saves.launch {
            progressRepository.setRequirementSignOffAndComment(
                advancementId,
                number,
                signOffText,
                commentText,
                catalogRepository.getAdvancements().badgeStart(advancementId, today())
            )
        }
    }

    /** The fields that differ from what's [recorded], each with its text. */
    private fun edits(recorded: RecordedRequirement): List<Pair<TextFieldState, String>> =
        listOf(signedOffBy to recorded.signedOffBy, comment to recorded.comment)
            .mapNotNull { (field, saved) ->
                field.text.toString().takeIf { normalizedText(it) != saved }?.let { field to it }
            }

    /**
     * Shows the [saved] text in [field] when it changes from [before] without the scout typing
     * it, as when it's cleared, unless the field has an unsaved edit. It's done as the change is
     * shown, so the field is never a frame behind it.
     */
    private fun followSaved(field: TextFieldState, before: String?, saved: String?) {
        if (saved == before || normalizedText(field.text.toString()) != before) return
        show(field, saved)
    }

    /**
     * Puts [text] in [field], in a snapshot of its own, so the field has changed before uiState
     * next reads it.
     */
    private fun show(field: TextFieldState, text: String?) {
        Snapshot.withMutableSnapshot { field.setTextAndPlaceCursorAtEnd(text.orEmpty()) }
    }

    /**
     * Clears everything recorded for this requirement and every one under it. A field without an
     * unsaved edit follows what it shows as it's cleared ([followSaved]). An unsaved edit is
     * discarded once the clear is saved, unless the scout changed it while it was being saved, as
     * that came after. A clear that fails keeps it.
     */
    fun clear() {
        // The button shows only once the page has.
        val shown = shown ?: return
        val edits = edits(shown)
        saves.launch {
            recorder.clear(shown.numbersWithin)
            edits.forEach { (field, edit) -> if (field.text.toString() == edit) show(field, null) }
        }
    }

    /** The scout has been told about [failure]. */
    fun onSaveFailureShown(failure: TaskFailure) {
        saves.onShown(failure)
    }

    /** The latest date the scout can give as a completion date, read from the clock each time. */
    fun today(): LocalDate = LocalDate.now(clock)

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("advancementId") advancementId: String,
            @Assisted("number") number: String
        ): RequirementDetailViewModel
    }

    private companion object {
        const val SIGNED_OFF_BY = "signedOffBy"
        const val COMMENT = "comment"
    }
}
