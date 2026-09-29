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
        /**
         * The top-level requirements of the version the scout works on, or null if the
         * badge was started on a version that isn't in the catalog.
         */
        val requirements: List<RequirementItem>?
    ) : BadgeDetailUiState
}
