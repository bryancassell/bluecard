package io.github.bryancassell.bluecard.ui.onboarding

/** What the Onboarding screen shows. */
data class OnboardingUiState(
    val name: String = "",
    val unitNumber: String = "",
    val saveStatus: SaveStatus = SaveStatus.Editing
) {
    /** The fields can be changed until a save starts, and again if it fails. */
    val canEdit: Boolean get() = saveStatus == SaveStatus.Editing || saveStatus == SaveStatus.Failed

    /** Both fields are required. */
    val canSave: Boolean get() = canEdit && name.isNotBlank() && unitNumber.isNotBlank()
}

enum class SaveStatus {
    Editing,
    Saving,

    /** The profile is saved; the navigation root replaces Onboarding with Home. */
    Saved,

    /** Saving failed, for example because the disk is full; the scout can try again. */
    Failed
}
