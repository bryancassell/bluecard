package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.progress.Counselor
import io.github.bryancassell.bluecard.ui.SaveFailure
import io.github.bryancassell.bluecard.ui.badges.EagleRequirement

/** What the Badge detail screen shows. */
sealed interface BadgeDetailUiState {
    /** The catalog is still loading. */
    data object Loading : BadgeDetailUiState

    /** The catalog or progress couldn't be read. */
    data object LoadFailed : BadgeDetailUiState

    data class Ready(
        val name: String,
        val summary: String,
        /** Null for a badge that isn't Eagle-required. */
        val eagle: EagleRequirement?,
        val officialUrl: String,
        /** The top-level requirements of the version the scout works on. */
        val requirements: List<RequirementItem>,
        /** The scout's merit badge counselor, or null if they haven't entered one. */
        val counselor: Counselor? = null,
        /** Something the scout recorded couldn't be saved, and they haven't been told yet. */
        val saveFailure: SaveFailure? = null
    ) : BadgeDetailUiState

    /**
     * The catalog doesn't have the badge or its requirements version ([badgeRequirements]).
     * Only a catalog edited during development can cause that.
     */
    data object Unavailable : BadgeDetailUiState
}
