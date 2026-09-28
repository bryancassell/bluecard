package io.github.bryancassell.bluecard

import androidx.navigation3.runtime.NavKey

sealed interface MainActivityUiState {
    /** The saved profile is still loading; the splash screen stays up. */
    data object Loading : MainActivityUiState

    data class Ready(val startDestination: NavKey) : MainActivityUiState
}
