package io.github.bryancassell.bluecard.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val profileRepository: ProfileRepository) :
    ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun onUnitNumberChange(unitNumber: String) {
        _uiState.update { it.copy(unitNumber = unitNumber) }
    }

    /** Saves the profile, trimmed. Does nothing unless [OnboardingUiState.canSave]. */
    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            profileRepository.saveProfile(Profile(state.name.trim(), state.unitNumber.trim()))
            _uiState.update { it.copy(isSaving = false, isSaved = true) }
        }
    }
}
