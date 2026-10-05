package io.github.bryancassell.bluecard.ui.ranks

import io.github.bryancassell.bluecard.data.progress.RankStanding
import io.github.bryancassell.bluecard.data.progress.RankStatus

/** What the Ranks screen shows. */
sealed interface RanksUiState {
    /** The catalog is still loading. */
    data object Loading : RanksUiState

    /** The catalog or progress couldn't be read. */
    data object LoadFailed : RanksUiState

    /** Every rank in the catalog, in the order they're earned. */
    data class Ready(val ranks: List<RankListItem>) : RanksUiState
}

/** One rank in the list. */
data class RankListItem(
    val id: String,
    val name: String,
    val status: RankStatus,
    /**
     * How much of the rank is done, from 0 to 1, while it's in progress, or once it's started if
     * it isn't earned. Null otherwise.
     */
    val fractionDone: Float? = null
)

/** The rank's row, with the scout's standing on it. Home shows the rank in progress in it too. */
fun RankStanding.toListItem() = RankListItem(rank.id, rank.name, status, fractionDone)
