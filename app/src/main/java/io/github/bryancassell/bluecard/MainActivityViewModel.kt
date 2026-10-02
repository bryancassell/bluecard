package io.github.bryancassell.bluecard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.data.progress.DamagedProgressRepository
import io.github.bryancassell.bluecard.ui.catchLoadFailure
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Whether the scout's profile is saved, which decides between Onboarding and Home, and whether
 * to tell the scout that their progress was damaged.
 */
@HiltViewModel
class MainActivityViewModel @Inject constructor(
    profileRepository: ProfileRepository,
    private val damagedProgressRepository: DamagedProgressRepository
) : ViewModel() {
    val uiState: StateFlow<MainActivityUiState> = combine(
        profileRepository.observeProfile(),
        damagedProgressRepository.observeNoticePending()
    ) { profile, noticePending ->
        MainActivityUiState.Ready(
            isSetUp = profile != null,
            showDamagedProgressNotice = noticePending
        )
    }
        .catchLoadFailure(MainActivityUiState.LoadFailed)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainActivityUiState.Loading)

    fun dismissDamagedProgressNotice() {
        viewModelScope.launch { damagedProgressRepository.dismissNotice() }
    }
}
