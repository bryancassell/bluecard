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
import io.github.bryancassell.bluecard.data.progress.Counselor
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.ui.SaveFailure
import io.github.bryancassell.bluecard.ui.SaveRunner
import io.github.bryancassell.bluecard.ui.catchLoadFailure
import io.github.bryancassell.bluecard.ui.keepText
import io.github.bryancassell.bluecard.ui.restoredText
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * A badge's merit badge counselor, in fields the scout edits and then saves. Once saved, the
 * page closes.
 */
@HiltViewModel(assistedFactory = EditCounselorViewModel.Factory::class)
class EditCounselorViewModel @AssistedInject constructor(
    @Assisted private val badgeId: String,
    catalogRepository: CatalogRepository,
    private val progressRepository: ProgressRepository,
    clock: Clock,
    // Keeps unsaved fields if the system stops the app in the background.
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    // The fields start as the saved counselor once it loads, or as the unsaved fields the
    // system stopped the app with. They're kept together, so if one was kept, all were.
    private var fieldsLoaded = savedStateHandle.restoredText(NAME) != null

    /** The counselor's name field, which the field edits directly. */
    val name = TextFieldState(savedStateHandle.restoredText(NAME).orEmpty())

    /** The counselor's phone number field. */
    val phone = TextFieldState(savedStateHandle.restoredText(PHONE).orEmpty())

    /** The counselor's email address field. */
    val email = TextFieldState(savedStateHandle.restoredText(EMAIL).orEmpty())

    init {
        // Kept only once the saved counselor has loaded into them, so if the system stops the
        // app before then, the page loads the saved counselor again.
        if (fieldsLoaded) keepFields()
    }

    private val saves = SaveRunner(viewModelScope)
    private val recorder = ProgressRecorder(badgeId, catalogRepository, progressRepository, clock)
    private val saved = MutableStateFlow(false)

    val uiState: StateFlow<EditCounselorUiState> = combine(
        flow { emit(catalogRepository.getBadges()) },
        progressRepository.observeProgress(badgeId),
        snapshotFlow { fields() },
        saved,
        saves.failure
    ) { catalog, progress, typed, saved, saveFailure ->
        // The badge is started on the version its pages show, so saving needs that version.
        val found = catalog.badgeRequirements(badgeId, progress)
            ?: return@combine EditCounselorUiState.Unavailable
        val stored = progress?.badge?.counselor
        val shown = if (fieldsLoaded) typed else loadFields(stored)
        EditCounselorUiState.Ready(
            badgeName = found.badge.name,
            changed = shown.normalized() != stored,
            saved = saved,
            saveFailure = saveFailure
        )
    }.catchLoadFailure(EditCounselorUiState.LoadFailed).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        EditCounselorUiState.Loading
    )

    /** What the fields hold. */
    private fun fields() =
        Counselor(name.text.toString(), phone.text.toString(), email.text.toString())

    /** Puts the [saved] counselor in the fields, the first time the page loads. */
    private fun loadFields(saved: Counselor?): Counselor {
        fieldsLoaded = true
        keepFields()
        // In a snapshot of its own, so the fields' observers, such as uiState, see the change as
        // soon as it's applied, not when Compose next applies changes made outside a snapshot.
        Snapshot.withMutableSnapshot {
            name.setTextAndPlaceCursorAtEnd(saved?.name.orEmpty())
            phone.setTextAndPlaceCursorAtEnd(saved?.phone.orEmpty())
            email.setTextAndPlaceCursorAtEnd(saved?.email.orEmpty())
        }
        return fields()
    }

    private fun keepFields() {
        savedStateHandle.keepText(NAME, name)
        savedStateHandle.keepText(PHONE, phone)
        savedStateHandle.keepText(EMAIL, email)
    }

    /**
     * Saves the fields as the badge's counselor, then closes the page. Empty fields are removed,
     * and with every field empty, so is the counselor.
     */
    fun save() {
        val counselor = fields()
        saves.launch {
            progressRepository.setCounselor(badgeId, counselor, recorder.badgeStart())
            saved.value = true
        }
    }

    /** The scout has been told about [failure]. */
    fun onSaveFailureShown(failure: SaveFailure) {
        saves.onShown(failure)
    }

    @AssistedFactory
    interface Factory {
        fun create(badgeId: String): EditCounselorViewModel
    }

    private companion object {
        const val NAME = "name"
        const val PHONE = "phone"
        const val EMAIL = "email"
    }
}
