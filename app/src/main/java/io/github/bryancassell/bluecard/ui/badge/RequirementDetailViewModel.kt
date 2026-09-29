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
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.normalizedComment
import io.github.bryancassell.bluecard.ui.SaveFailure
import io.github.bryancassell.bluecard.ui.SaveRunner
import io.github.bryancassell.bluecard.ui.catchLoadFailure
import io.github.bryancassell.bluecard.ui.keepText
import io.github.bryancassell.bluecard.ui.restoredText
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * One requirement of a badge and its sub-requirements, with the scout's progress: whether
 * it's complete and when, and their comment on it.
 */
@HiltViewModel(assistedFactory = RequirementDetailViewModel.Factory::class)
class RequirementDetailViewModel @AssistedInject constructor(
    @Assisted("badgeId") private val badgeId: String,
    @Assisted("number") private val number: String,
    catalogRepository: CatalogRepository,
    private val progressRepository: ProgressRepository,
    private val clock: Clock,
    // Keeps an unsaved comment if the system stops the app in the background.
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val restoredComment = savedStateHandle.restoredText(COMMENT)

    /**
     * The comment field's text, which the field edits directly. It starts as the saved comment
     * once that loads, or as the unsaved comment the system stopped the app with.
     */
    val comment = TextFieldState(restoredComment.orEmpty())
    private var commentLoaded = restoredComment != null

    init {
        // Kept only once the saved comment has loaded into it, so if the system stops the app
        // before then, the page loads the saved comment again.
        if (commentLoaded) savedStateHandle.keepText(COMMENT, comment)
    }

    private val saves = SaveRunner(viewModelScope)
    private val recorder = ProgressRecorder(badgeId, catalogRepository, progressRepository, clock)

    val uiState: StateFlow<RequirementDetailUiState> = combine(
        flow { emit(catalogRepository.getBadges()) },
        progressRepository.observeProgress(badgeId),
        snapshotFlow { comment.text.toString() },
        saves.failure
    ) { catalog, progress, commentText, saveFailure ->
        // Checked on every change, not only when the page opens, because which version
        // the badge uses depends on its progress.
        val found = catalog.badgeRequirements(badgeId, progress)
        val requirement = found?.version?.find(number)
            ?: return@combine RequirementDetailUiState.Unavailable
        val recorded = found.recorded[number]
        val text = if (commentLoaded) commentText else loadComment(recorded?.comment)
        RequirementDetailUiState.Ready(
            badgeName = found.badge.name,
            requirement = requirement.toItem(found.recorded),
            completedDate = recorded?.completedDate,
            children = requirement.children.map { it.toItem(found.recorded) },
            commentChanged = normalizedComment(text) != recorded?.comment,
            today = today(),
            saveFailure = saveFailure
        )
    }.catchLoadFailure(RequirementDetailUiState.LoadFailed).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        RequirementDetailUiState.Loading
    )

    /** Puts the [saved] comment in the field, the first time the page loads. */
    private fun loadComment(saved: String?): String {
        commentLoaded = true
        savedStateHandle.keepText(COMMENT, comment)
        // In a snapshot of its own, so the field's observers, such as uiState, see the change as
        // soon as it's applied, not when Compose next applies changes made outside a snapshot.
        Snapshot.withMutableSnapshot { comment.setTextAndPlaceCursorAtEnd(saved.orEmpty()) }
        return saved.orEmpty()
    }

    /**
     * Marks [requirementNumber], this requirement or one of its sub-requirements, completed or
     * not. Only for one without sub-requirements of its own.
     */
    fun setCompleted(requirementNumber: String, completed: Boolean) {
        saves.launch { recorder.setCompleted(requirementNumber, completed) }
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
