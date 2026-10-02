package io.github.bryancassell.bluecard.ui.profile

import io.github.bryancassell.bluecard.ui.SaveFailure

/**
 * What the Edit name and unit screen shows, apart from the fields' text, which the fields edit
 * in [EditProfileViewModel].
 */
sealed interface EditProfileUiState {
    /** The saved name and unit number are still loading. */
    data object Loading : EditProfileUiState

    /** The saved name and unit number couldn't be read. */
    data object LoadFailed : EditProfileUiState

    data class Ready(
        /** Whether the fields differ from the saved ones and neither is blank. */
        val canSave: Boolean,
        /**
         * Whether the fields differ from the saved ones, even if one is blank, until they're
         * saved, so Back asks before discarding them.
         */
        val changed: Boolean,
        /** The fields have been saved, so the page can close. */
        val saved: Boolean = false,
        /** The fields couldn't be saved, and the scout hasn't been told yet. */
        val saveFailure: SaveFailure? = null
    ) : EditProfileUiState
}
