package io.github.bryancassell.bluecard.ui.home

import io.github.bryancassell.bluecard.data.progress.RankStatus
import io.github.bryancassell.bluecard.ui.ranks.RankListItem

/**
 * Every rank, as on Ranks: the first [earned] of them earned, and the next in progress with
 * [fractionDone] of it done.
 */
fun ranks(earned: Int, fractionDone: Float? = 0f) = listOf(
    "scout" to "Scout",
    "tenderfoot" to "Tenderfoot",
    "second-class" to "Second Class",
    "first-class" to "First Class",
    "star" to "Star",
    "life" to "Life",
    "eagle" to "Eagle Scout"
).mapIndexed { index, (id, name) ->
    when {
        index < earned -> RankListItem(id, name, RankStatus.Earned)
        index == earned -> RankListItem(id, name, RankStatus.InProgress, fractionDone)
        else -> RankListItem(id, name, RankStatus.NotEarned)
    }
}
