package io.github.bryancassell.bluecard.ui.onboarding

/**
 * What the Onboarding screen shows, apart from the fields' text, which the fields edit in
 * [OnboardingViewModel].
 */
data class OnboardingUiState(
    val saveStatus: SaveStatus = SaveStatus.Editing,

    /** Both fields are required; this is true once neither is blank. */
    val isComplete: Boolean = false
) {
    /** The fields can be changed until a save starts, and again if it fails. */
    val canEdit: Boolean get() = saveStatus == SaveStatus.Editing || saveStatus == SaveStatus.Failed

    val canSave: Boolean get() = canEdit && isComplete
}

enum class SaveStatus {
    Editing,
    Saving,

    /** The profile is saved; the navigation root replaces Onboarding with Home. */
    Saved,

    /** Saving failed, for example because the disk is full; the scout can try again. */
    Failed
}
