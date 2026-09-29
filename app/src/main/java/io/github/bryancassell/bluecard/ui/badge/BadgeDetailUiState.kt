package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.ui.badges.EagleRequirement

/** What the Badge detail screen shows. */
sealed interface BadgeDetailUiState {
    /** The catalog is still loading. */
    data object Loading : BadgeDetailUiState

    data class Ready(
        val name: String,
        val summary: String,
        /** Null for a badge that isn't Eagle-required. */
        val eagle: EagleRequirement?,
        val officialUrl: String,
        /** The top-level requirements of the version the scout works on. */
        val requirements: List<RequirementItem>
    ) : BadgeDetailUiState

    /** The catalog doesn't have the badge or its requirements version ([badgeRequirements]). */
    data object Unavailable : BadgeDetailUiState
}
