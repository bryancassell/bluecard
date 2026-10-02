package io.github.bryancassell.bluecard.ui.badge

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
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.normalizedText
import io.github.bryancassell.bluecard.ui.SaveFailure
import io.github.bryancassell.bluecard.ui.SaveRunner
import io.github.bryancassell.bluecard.ui.StoredTextFields
import io.github.bryancassell.bluecard.ui.catchLoadFailure
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * One requirement of a badge and its sub-requirements, with the scout's progress: whether
 * it's complete and when, their comment on it, and its tracker.
 */
@HiltViewModel(assistedFactory = RequirementDetailViewModel.Factory::class)
class RequirementDetailViewModel @AssistedInject constructor(
    @Assisted("badgeId") private val badgeId: String,
    @Assisted("number") private val number: String,
    private val catalogRepository: CatalogRepository,
    private val progressRepository: ProgressRepository,
    private val clock: Clock,
    // Keeps an unsaved comment if the system stops the app in the background.
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val fields = StoredTextFields(savedStateHandle, COMMENT)

    /**
     * The comment field's text, which the field edits directly. It starts as the saved comment
     * once that loads, or as the unsaved comment the system stopped the app with.
     */
    val comment = fields[COMMENT]

    private val saves = SaveRunner(viewModelScope)
    private val recorder =
        CompletionRecorder(badgeId, number, catalogRepository, progressRepository, clock)

    /**
     * The requirement as recorded, or null if the badge's requirements don't have it. It's
     * built when the catalog or progress changes, not on each keystroke in the comment.
     */
    private val recorded = combine(
        flow { emit(catalogRepository.getBadges()) },
        progressRepository.observeProgress(badgeId)
    ) { catalog, progress ->
        // Checked on every change, not only when the page opens, because which version
        // the badge uses depends on its progress.
        val found = catalog.badgeRequirements(badgeId, progress)
        found?.version?.find(number)?.let { requirement ->
            val recorded = found.recorded[number]
            val numbersWithin = requirement.numbersWithin()
            RecordedRequirement(
                badgeName = found.badge.name,
                requirement = found.item(requirement),
                completedDate = recorded?.completedDate,
                children = requirement.children.map(found::item),
                tracker = requirement.tracker?.toItem(found.trackerEntries[number].orEmpty()),
                comment = recorded?.comment,
                numbersWithin = numbersWithin,
                hasRecorded = found.hasRecorded(numbersWithin)
            )
        }
    }

    private class RecordedRequirement(
        val badgeName: String,
        val requirement: RequirementItem,
        val completedDate: LocalDate?,
        val children: List<RequirementItem>,
        val tracker: TrackerItem?,
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
        snapshotFlow { comment.text.toString() },
        saves.failure
    ) { recorded, _, saveFailure ->
        if (recorded == null) return@combine RequirementDetailUiState.Unavailable
        fields.loadOnce { mapOf(COMMENT to recorded.comment) }
        shown?.let { followSavedComment(before = it.comment, saved = recorded.comment) }
        shown = recorded
        RequirementDetailUiState.Ready(
            badgeName = recorded.badgeName,
            requirement = recorded.requirement,
            completedDate = recorded.completedDate,
            children = recorded.children,
            tracker = recorded.tracker,
            commentChanged = normalizedText(comment.text.toString()) != recorded.comment,
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
     * null removes it.
     */
    fun setCompletedDate(date: LocalDate?) {
        saves.launch { progressRepository.setRequirementCompletedDate(badgeId, number, date) }
    }

    /** Saves the comment field as this requirement's comment. An empty one removes it. */
    fun saveComment() {
        val text = comment.text.toString()
        saves.launch {
            progressRepository.setRequirementComment(
                badgeId,
                number,
                text,
                catalogRepository.badgeStart(badgeId, clock)
            )
        }
    }

    /**
     * Shows the [saved] comment in the comment field when it changes from [before] without the
     * scout typing it, as when it's cleared, unless the field has an unsaved edit. It's done as
     * the change is shown, so the field is never a frame behind it.
     */
    private fun followSavedComment(before: String?, saved: String?) {
        if (saved == before || normalizedText(comment.text.toString()) != before) return
        showComment(saved)
    }

    /**
     * Puts [text] in the comment field, in a snapshot of its own, so the field has changed
     * before uiState next reads it.
     */
    private fun showComment(text: String?) {
        Snapshot.withMutableSnapshot { comment.setTextAndPlaceCursorAtEnd(text.orEmpty()) }
    }

    /**
     * Clears everything recorded for this requirement and every one under it. Without an
     * unsaved edit, the comment field follows the comment as it's cleared ([followSavedComment]).
     * An unsaved edit is discarded once the clear is saved, unless the scout changed it while it
     * was being saved, as that came after. A clear that fails keeps it.
     */
    fun clear() {
        // The button shows only once the page has.
        val shown = shown ?: return
        val edit = comment.text.toString().takeIf { normalizedText(it) != shown.comment }
        saves.launch {
            recorder.clear(shown.numbersWithin)
            if (edit != null && comment.text.toString() == edit) showComment(null)
        }
    }

    /** The scout has been told about [failure]. */
    fun onSaveFailureShown(failure: SaveFailure) {
        saves.onShown(failure)
    }

    /** The latest date the scout can give as a completion date, read from the clock each time. */
    fun today(): LocalDate = LocalDate.now(clock)

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("badgeId") badgeId: String,
            @Assisted("number") number: String
        ): RequirementDetailViewModel
    }

    private companion object {
        const val COMMENT = "comment"
    }
}
