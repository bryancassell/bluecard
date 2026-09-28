package io.github.bryancassell.bluecard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.ui.navigation.Home
import io.github.bryancassell.bluecard.ui.navigation.Onboarding
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Decides where the app starts: Onboarding until a profile is saved, then Home. */
@HiltViewModel
class MainActivityViewModel @Inject constructor(profileRepository: ProfileRepository) :
    ViewModel() {
    val uiState: StateFlow<MainActivityUiState> = profileRepository.observeProfile()
        .map { MainActivityUiState.Ready(startDestination = if (it == null) Onboarding else Home) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainActivityUiState.Loading)
}
