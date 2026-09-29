package io.github.bryancassell.bluecard

sealed interface MainActivityUiState {
    /** The saved profile is still loading; the splash screen stays up. */
    data object Loading : MainActivityUiState

    /** The saved profile couldn't be read. */
    data object LoadFailed : MainActivityUiState

    /** [isSetUp] is true once the scout's profile is saved. */
    data class Ready(val isSetUp: Boolean) : MainActivityUiState
}
