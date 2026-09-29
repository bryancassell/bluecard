package io.github.bryancassell.bluecard.ui.home

/** What the Home screen shows. */
sealed interface HomeUiState {
    /** The profile, catalog or progress is still loading. */
    data object Loading : HomeUiState

    /** The profile, catalog or progress couldn't be read. */
    data object LoadFailed : HomeUiState

    data class Ready(
        val name: String,
        val unitNumber: String,
        /** Every badge in the catalog that the scout has started. */
        val badges: ProgressCounts,
        /**
         * Eagle-required badges, counting each "one of" group (such as Cycling, Hiking
         * and Swimming) once, since earning any badge in the group meets the requirement.
         */
        val eagle: ProgressCounts,
        /** How many Eagle-required badges the catalog has, counting each group once. */
        val eagleTotal: Int
    ) : HomeUiState {
        /** The scout hasn't started a badge yet. */
        val hasNoProgress: Boolean get() = badges == ProgressCounts(completed = 0, inProgress = 0)
    }
}

data class ProgressCounts(val completed: Int, val inProgress: Int)
