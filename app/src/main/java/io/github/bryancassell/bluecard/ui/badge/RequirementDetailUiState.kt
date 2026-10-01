package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.ui.SaveFailure
import java.time.LocalDate

/** What the Requirement detail screen shows. */
sealed interface RequirementDetailUiState {
    /** The catalog is still loading. */
    data object Loading : RequirementDetailUiState

    /** The catalog or progress couldn't be read. */
    data object LoadFailed : RequirementDetailUiState

    data class Ready(
        val badgeName: String,
        val requirement: RequirementItem,
        /**
         * When the scout completed it, for a requirement they marked complete, or its own work,
         * for one with own work they marked complete ([RequirementItem.ownWork]). Null if they
         * gave no date or it isn't marked complete.
         */
        val completedDate: LocalDate?,
        val children: List<RequirementItem>,
        /** Its tracker, or null if it has none. */
        val tracker: TrackerItem?,
        /** Whether the comment field differs from the saved comment, so it can be saved. */
        val commentChanged: Boolean,
        /**
         * Whether anything is recorded for this requirement or one under it, which the scout can
         * clear.
         */
        val canClear: Boolean,
        /** Something the scout recorded couldn't be saved, and they haven't been told yet. */
        val saveFailure: SaveFailure? = null
    ) : RequirementDetailUiState

    /**
     * The catalog doesn't have the badge or its requirements version ([badgeRequirements]),
     * or that version doesn't have this requirement. The last can also happen with a released
     * catalog, when the badge moves to another version, such as when its progress is cleared.
     */
    data object Unavailable : RequirementDetailUiState
}
