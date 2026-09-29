package io.github.bryancassell.bluecard.ui.onboarding

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    // Keeps what the scout typed if the system stops the app in the background.
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        OnboardingUiState(
            name = savedStateHandle[NAME] ?: "",
            unitNumber = savedStateHandle[UNIT_NUMBER] ?: ""
        )
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onNameChange(name: String) {
        savedStateHandle[NAME] = name
        _uiState.update { it.copy(name = name) }
    }

    fun onUnitNumberChange(unitNumber: String) {
        savedStateHandle[UNIT_NUMBER] = unitNumber
        _uiState.update { it.copy(unitNumber = unitNumber) }
    }

    /** Saves the profile, trimmed. Does nothing unless [OnboardingUiState.canSave]. */
    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(saveStatus = SaveStatus.Saving) }
        viewModelScope.launch {
            val status = try {
                profileRepository.saveProfile(Profile(state.name.trim(), state.unitNumber.trim()))
                SaveStatus.Saved
            } catch (e: IOException) {
                // The app reports caught exceptions nowhere else, so logcat and bug reports
                // are the only way to tell why saving failed.
                Log.w(TAG, "Couldn't save the profile", e)
                SaveStatus.Failed
            }
            _uiState.update { it.copy(saveStatus = status) }
        }
    }

    private companion object {
        const val TAG = "Onboarding"
        const val NAME = "name"
        const val UNIT_NUMBER = "unit_number"
    }
}
