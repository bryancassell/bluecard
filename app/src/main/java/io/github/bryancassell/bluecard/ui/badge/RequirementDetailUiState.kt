package io.github.bryancassell.bluecard.ui.badge

/** What the Requirement detail screen shows. */
sealed interface RequirementDetailUiState {
    /** The catalog is still loading. */
    data object Loading : RequirementDetailUiState

    data class Ready(
        val badgeName: String,
        val requirement: RequirementItem,
        /** Its sub-requirements; empty for a requirement that has only a tracker. */
        val children: List<RequirementItem>
    ) : RequirementDetailUiState
}
