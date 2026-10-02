package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.ui.TaskFailure

/** What the Edit counselor screen shows. */
sealed interface EditCounselorUiState {
    /** The catalog or the saved counselor is still loading. */
    data object Loading : EditCounselorUiState

    /** The catalog or progress couldn't be read. */
    data object LoadFailed : EditCounselorUiState

    data class Ready(
        val badgeName: String,
        /**
         * Whether the fields differ from the saved counselor, until they're saved, so they can
         * be saved, and Back asks before discarding them.
         */
        val changed: Boolean,
        /** The fields have been saved, so the page can close. */
        val saved: Boolean = false,
        /** The fields couldn't be saved, and the scout hasn't been told yet. */
        val saveFailure: TaskFailure? = null
    ) : EditCounselorUiState

    /**
     * The catalog doesn't have the badge or its requirements version ([badgeRequirements]).
     * Only a catalog edited during development can cause that.
     */
    data object Unavailable : EditCounselorUiState
}
