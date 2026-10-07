package io.github.bryancassell.bluecard.ui.onboarding

import io.github.bryancassell.bluecard.ui.TaskFailure

/**
 * What the Onboarding screen shows, apart from the fields' text, which the fields edit in
 * [OnboardingViewModel].
 */
data class OnboardingUiState(
    val saveStatus: SaveStatus = SaveStatus.Editing,

    /** Both fields are required; this is true once neither is blank. */
    val isComplete: Boolean = false,

    /** A save that failed, for the screen to tell the scout about. */
    val saveFailure: TaskFailure? = null
) {
    /** The fields can be changed until a save starts, and again if it fails. */
    val canEdit: Boolean get() = saveStatus == SaveStatus.Editing

    val canSave: Boolean get() = canEdit && isComplete
}

enum class SaveStatus {
    /**
     * Not saved yet. A save that failed, for example because the disk is full, comes back here,
     * so the scout can try again.
     */
    Editing,
    Saving,

    /** The profile is saved; the navigation root replaces Onboarding with Home. */
    Saved
}
