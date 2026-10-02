package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.ui.TaskFailure
import java.time.LocalDate

/** What the Requirement detail screen shows. */
sealed interface RequirementDetailUiState {
    /** The catalog is still loading. */
    data object Loading : RequirementDetailUiState

    /** The catalog or progress couldn't be read. */
    data object LoadFailed : RequirementDetailUiState

    data class Ready(
        /** The name of the badge or rank the requirement is part of. */
        val advancementName: String,
        val requirement: RequirementItem,
        /**
         * When the scout completed it, for a requirement they marked complete, or its own work,
         * for one with own work they marked complete ([RequirementItem.ownWork]). For one that's
         * complete once its tracker's rows are ([RequirementItem.completesFromRows]), it's when
         * it was completed. Null if there's no date or it isn't complete.
         */
        val completedDate: LocalDate?,
        /**
         * For one that's complete once its tracker's rows are, the date its last row was first
         * saved, where Add date opens the picker once the scout removed the date. Null until
         * every row is filled in, or for any other requirement.
         */
        val rowsCompletedDate: LocalDate? = null,
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
        val saveFailure: TaskFailure? = null
    ) : RequirementDetailUiState

    /**
     * The catalog doesn't have the badge or rank or its requirements version
     * ([advancementRequirements]), or that version doesn't have this requirement. The last can
     * also happen with a released catalog, when the badge or rank moves to another version, such
     * as when its progress is cleared.
     */
    data object Unavailable : RequirementDetailUiState
}
