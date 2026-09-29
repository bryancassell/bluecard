package io.github.bryancassell.bluecard.ui.badge

/** What the Requirement detail screen shows. */
sealed interface RequirementDetailUiState {
    /** The catalog is still loading. */
    data object Loading : RequirementDetailUiState

    /** The catalog or progress couldn't be read. */
    data object LoadFailed : RequirementDetailUiState

    data class Ready(
        val badgeName: String,
        val requirement: RequirementItem,
        val children: List<RequirementItem>
    ) : RequirementDetailUiState

    /**
     * The catalog doesn't have the badge or its requirements version ([badgeRequirements]),
     * or that version doesn't have this requirement. The last can also happen with a released
     * catalog, when the badge moves to another version, such as when its progress is cleared.
     */
    data object Unavailable : RequirementDetailUiState
}
