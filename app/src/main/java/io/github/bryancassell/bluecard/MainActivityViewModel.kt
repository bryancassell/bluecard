package io.github.bryancassell.bluecard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Whether the scout's profile is saved, which decides between Onboarding and Home. */
@HiltViewModel
class MainActivityViewModel @Inject constructor(profileRepository: ProfileRepository) :
    ViewModel() {
    val uiState: StateFlow<MainActivityUiState> = profileRepository.observeProfile()
        .map { it != null }
        .distinctUntilChanged()
        .map { MainActivityUiState.Ready(isSetUp = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainActivityUiState.Loading)
}
