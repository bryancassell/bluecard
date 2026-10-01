package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.foundation.text.input.clearText
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * One requirement of a badge and its sub-requirements, with the scout's progress: whether
 * it's complete and when, their comment on it, and its tracker.
 */
@HiltViewModel(assistedFactory = RequirementDetailViewModel.Factory::class)
class RequirementDetailViewModel @AssistedInject constructor(
    @Assisted("badgeId") private val badgeId: String,
    @Assisted("number") private val number: String,
    catalogRepository: CatalogRepository,
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
    private val recorder = ProgressRecorder(badgeId, catalogRepository, progressRepository, clock)

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
     * The numbers of this requirement and of every one under it, once the page has shown it, so
     * [clear] queues its write as soon as it's called, in order with the page's other changes.
     */
    private var numbersWithin: List<String>? = null

    val uiState: StateFlow<RequirementDetailUiState> = combine(
        recorded.onEach { numbersWithin = it?.numbersWithin },
        // Works the state out again as the scout types.
        snapshotFlow { comment.text.toString() },
        saves.failure
    ) { recorded, _, saveFailure ->
        if (recorded == null) return@combine RequirementDetailUiState.Unavailable
        fields.loadOnce { mapOf(COMMENT to recorded.comment) }
        RequirementDetailUiState.Ready(
            badgeName = recorded.badgeName,
            requirement = recorded.requirement,
            completedDate = recorded.completedDate,
            children = recorded.children,
            tracker = recorded.tracker,
            commentChanged = normalizedText(comment.text.toString()) != recorded.comment,
            canClear = recorded.hasRecorded,
            today = today(),
            saveFailure = saveFailure
        )
    }.catchLoadFailure(RequirementDetailUiState.LoadFailed).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        RequirementDetailUiState.Loading
    )

    /** Marks this requirement, one the scout marks by hand, completed or not. */
    fun setCompleted(completed: Boolean) {
        saves.launch { recorder.setCompleted(number, completed) }
    }

    /** Changes the date this requirement, which is complete, was completed on; null removes it. */
    fun setCompletedDate(date: LocalDate?) {
        saves.launch { progressRepository.setRequirementCompletedDate(badgeId, number, date) }
    }

    /** Saves the comment field as this requirement's comment. An empty one removes it. */
    fun saveComment() {
        val text = comment.text.toString()
        saves.launch {
            progressRepository.setRequirementComment(badgeId, number, text, recorder.badgeStart())
        }
    }

    /**
     * Clears everything recorded for this requirement and every one under it, then empties the
     * comment field, discarding an unsaved edit too. The field keeps its text if the clear
     * fails, as the comment does.
     */
    fun clear() {
        // The button shows only once the page has.
        val numbers = numbersWithin ?: return
        saves.launch {
            recorder.clear(numbers)
            // In a snapshot of its own, so uiState sees the change straight away.
            Snapshot.withMutableSnapshot { comment.clearText() }
        }
    }

    /** The scout has been told about [failure]. */
    fun onSaveFailureShown(failure: SaveFailure) {
        saves.onShown(failure)
    }

    private fun today() = LocalDate.now(clock)

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
