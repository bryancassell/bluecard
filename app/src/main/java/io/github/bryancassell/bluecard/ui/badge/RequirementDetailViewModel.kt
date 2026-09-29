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
import io.github.bryancassell.bluecard.ui.SaveFailure
import io.github.bryancassell.bluecard.ui.SaveRunner
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
 * it's complete and when, and their comment on it.
 */
@HiltViewModel(assistedFactory = RequirementDetailViewModel.Factory::class)
class RequirementDetailViewModel @AssistedInject constructor(
    @Assisted("badgeId") private val badgeId: String,
    @Assisted("number") private val number: String,
    private val catalogRepository: CatalogRepository,
    private val progressRepository: ProgressRepository,
    private val clock: Clock,
    // Keeps an unsaved comment if the system stops the app in the background.
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    /**
     * The comment field's text, which the field edits directly. It starts as the saved comment
     * once that loads, or as the unsaved comment the system stopped the app with.
     */
    val comment = TextFieldState(savedStateHandle[COMMENT] ?: "")
    private var commentLoaded = savedStateHandle.contains(COMMENT)

    private val saves = SaveRunner(viewModelScope)

    val uiState: StateFlow<RequirementDetailUiState> = combine(
        flow { emit(catalogRepository.getBadges()) },
        progressRepository.observeProgress(badgeId),
        // Saved as it changes, once the saved comment has loaded into it. The scout can change
        // it only while the screen shows the field, which is while it collects uiState.
        snapshotFlow { comment.text.toString() }.onEach {
            if (commentLoaded) savedStateHandle[COMMENT] = it
        },
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
            commentChanged = text.asStoredComment() != recorded?.comment,
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
        // In a snapshot of its own, so the field's observers, such as the flow that saves it,
        // see the change as soon as it's applied, not when Compose next applies changes made
        // outside a snapshot.
        Snapshot.withMutableSnapshot { comment.setTextAndPlaceCursorAtEnd(saved.orEmpty()) }
        return saved.orEmpty()
    }

    /**
     * Marks [requirementNumber], this requirement or one of its sub-requirements, completed
     * today or not. Only for one without sub-requirements of its own.
     */
    fun setCompleted(requirementNumber: String, completed: Boolean) {
        saves.launch {
            val badge = badge()
            progressRepository.setRequirementCompleted(badge, requirementNumber, completed, today())
        }
    }

    /** Changes the date this requirement, which is complete, was completed on; null removes it. */
    fun setCompletedDate(date: LocalDate?) {
        saves.launch { progressRepository.markRequirementCompleted(badgeId, number, date) }
    }

    /** Saves the comment field as this requirement's comment. An empty one removes it. */
    fun saveComment() {
        val text = comment.text.toString().asStoredComment()
        saves.launch {
            progressRepository.setRequirementComment(
                badgeId,
                number,
                text,
                badge().startedOn(today())
            )
        }
    }

    /** The scout has been told about [failure]. */
    fun onSaveFailureShown(failure: SaveFailure) {
        saves.onShown(failure)
    }

    private suspend fun badge() = catalogRepository.getBadges().first { it.id == badgeId }

    private fun today() = LocalDate.now(clock)

    /** The comment as it's saved: without spaces around it, and null if nothing is left. */
    private fun String.asStoredComment() = trim().ifEmpty { null }

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
