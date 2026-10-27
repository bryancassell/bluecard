package io.github.bryancassell.bluecard.ui.home

import io.github.bryancassell.bluecard.data.progress.RankStatus
import io.github.bryancassell.bluecard.ui.badges.BadgeListItem
import io.github.bryancassell.bluecard.ui.ranks.RankListItem

/** What the Home screen shows. */
sealed interface HomeUiState {
    /** The profile, catalog or progress is still loading. */
    data object Loading : HomeUiState

    /** The profile, catalog or progress couldn't be read. */
    data object LoadFailed : HomeUiState

    data class Ready(
        val name: String,
        val unitNumber: String,
        /**
         * Every rank, in the order they're earned, with the scout's standing on each, as on
         * Ranks.
         */
        val ranks: List<RankListItem>,
        /** Every badge in the catalog that the scout has started. */
        val badges: ProgressCounts,
        /**
         * Eagle-required badges, counting each "one of" group (such as Cycling, Hiking
         * and Swimming) once, since earning any badge in the group meets the requirement.
         */
        val eagle: ProgressCounts,
        /** How many Eagle-required badges the catalog has, counting each group once. */
        val eagleTotal: Int,
        /** Every badge in the catalog that the scout has in progress, in alphabetical order. */
        val badgesInProgress: List<BadgeListItem>
    ) : HomeUiState {
        /** The highest rank the scout has earned, or null before they've earned one. */
        val rank: RankListItem? get() = ranks.lastOrNull { it.status == RankStatus.Earned }

        /**
         * The rank the scout is working toward, the one in progress. Null once they've earned
         * every rank.
         */
        val nextRank: RankListItem? get() = ranks.find { it.status == RankStatus.InProgress }

        /** The scout hasn't started a badge yet. */
        val hasNoProgress: Boolean get() = badges == ProgressCounts(completed = 0, inProgress = 0)
    }
}

data class ProgressCounts(val completed: Int, val inProgress: Int)
