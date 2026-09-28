package io.github.bryancassell.bluecard.ui.onboarding

/** What the Onboarding screen shows. */
data class OnboardingUiState(
    val name: String = "",
    val unitNumber: String = "",
    val isSaving: Boolean = false,
    /** True once the profile is saved; the screen then moves on to Home. */
    val isSaved: Boolean = false
) {
    /** Both fields are required. */
    val canSave: Boolean get() = name.isNotBlank() && unitNumber.isNotBlank() && !isSaving &&
        !isSaved
}
