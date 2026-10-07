package io.github.bryancassell.bluecard.ui.onboarding

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.ui.TaskFailure
import io.github.bryancassell.bluecard.ui.TaskRunner
import io.github.bryancassell.bluecard.ui.textFieldState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    // Keeps what the scout typed if the system stops the app in the background.
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    /** The name field's text, which the field edits directly. */
    val name = savedStateHandle.textFieldState(NAME)

    /** The unit number field's text, which the field edits directly. */
    val unitNumber = savedStateHandle.textFieldState(UNIT_NUMBER)

    private val saveStatus = MutableStateFlow(SaveStatus.Editing)

    private val saves = TaskRunner(viewModelScope)

    val uiState: StateFlow<OnboardingUiState> = combine(
        snapshotFlow { name.text.toString() },
        snapshotFlow { unitNumber.text.toString() },
        saveStatus,
        saves.failure,
        ::uiStateOf
    ).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        // Starts from the restored text, so Save doesn't show as disabled until the flow
        // catches up.
        currentUiState()
    )

    /**
     * Saves the profile, trimmed. Does nothing while saving, once saved, or if either field is
     * blank. Checks the fields' text directly, so it can save before [uiState] catches up with
     * the last keystroke.
     */
    fun save() {
        if (!currentUiState().canSave) return
        val profile = Profile(name.text.toString().trim(), unitNumber.text.toString().trim())
        saveStatus.value = SaveStatus.Saving
        saves.launch {
            try {
                profileRepository.saveProfile(profile)
                saveStatus.value = SaveStatus.Saved
            } finally {
                // A save that failed unlocks the form, so the scout can try again.
                if (saveStatus.value == SaveStatus.Saving) saveStatus.value = SaveStatus.Editing
            }
        }
    }

    /** The scout has been told about [failure]. */
    fun onSaveFailureShown(failure: TaskFailure) {
        saves.onShown(failure)
    }

    /** What [uiState] shows once it catches up with the fields' current text. */
    private fun currentUiState() =
        uiStateOf(name.text, unitNumber.text, saveStatus.value, saves.failure.value)

    private fun uiStateOf(
        name: CharSequence,
        unitNumber: CharSequence,
        saveStatus: SaveStatus,
        saveFailure: TaskFailure?
    ) = OnboardingUiState(
        saveStatus,
        isComplete = name.isNotBlank() && unitNumber.isNotBlank(),
        saveFailure = saveFailure
    )

    private companion object {
        const val NAME = "name"
        const val UNIT_NUMBER = "unit_number"
    }
}
