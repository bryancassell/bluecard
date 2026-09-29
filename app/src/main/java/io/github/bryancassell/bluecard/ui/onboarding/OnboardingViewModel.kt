package io.github.bryancassell.bluecard.ui.onboarding

import android.util.Log
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.ui.textFieldState
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    val uiState: StateFlow<OnboardingUiState> = combine(
        snapshotFlow { name.text.toString() },
        snapshotFlow { unitNumber.text.toString() },
        saveStatus,
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
        viewModelScope.launch {
            saveStatus.value = try {
                profileRepository.saveProfile(profile)
                SaveStatus.Saved
            } catch (e: IOException) {
                // The app reports caught exceptions nowhere else, so logcat and bug reports
                // are the only way to tell why saving failed.
                Log.w(TAG, "Couldn't save the profile", e)
                SaveStatus.Failed
            }
        }
    }

    /** What [uiState] shows once it catches up with the fields' current text. */
    private fun currentUiState() = uiStateOf(name.text, unitNumber.text, saveStatus.value)

    private fun uiStateOf(name: CharSequence, unitNumber: CharSequence, saveStatus: SaveStatus) =
        OnboardingUiState(saveStatus, isComplete = name.isNotBlank() && unitNumber.isNotBlank())

    private companion object {
        const val TAG = "Onboarding"
        const val NAME = "name"
        const val UNIT_NUMBER = "unit_number"
    }
}
