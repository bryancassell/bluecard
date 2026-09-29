package io.github.bryancassell.bluecard.ui.badge

/** What the Requirement detail screen shows. */
sealed interface RequirementDetailUiState {
    /** The catalog is still loading. */
    data object Loading : RequirementDetailUiState

    data class Ready(
        val badgeName: String,
        val requirement: RequirementItem,
        val children: List<RequirementItem>
    ) : RequirementDetailUiState

    /**
     * The catalog doesn't have the badge, its requirements version ([badgeRequirements]), or
     * this requirement in that version.
     */
    data object Unavailable : RequirementDetailUiState
}
