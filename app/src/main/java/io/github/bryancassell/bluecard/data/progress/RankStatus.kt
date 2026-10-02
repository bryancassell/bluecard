package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.catalog.Rank

/** How far the scout has got with a rank. Ranks are earned in order, Scout through Eagle. */
enum class RankStatus {
    /** Not earned, and not the next rank to earn. */
    NotEarned,

    /** The lowest rank not yet earned: the next one to earn, even before it's started. */
    InProgress,

    Earned
}

/** The scout's standing on [rank], from their progress on it and on the ranks below it. */
data class RankStanding(
    val rank: Rank,
    val status: RankStatus,
    /**
     * How much of the rank is done, from 0 to 1, for its progress bar: while it's in progress,
     * or once it's started if it isn't earned. Null otherwise, and for a rank on a requirements
     * version missing from the catalog, which can't be measured.
     */
    val fractionDone: Float? = null,
    /**
     * The rank above this one that the scout marked earned on a prior date, which counts this
     * one as earned, when nothing else does: it isn't marked itself, and its requirements aren't
     * complete. Null otherwise. The nearest of them, if there's more than one.
     */
    val earnedWith: Rank? = null
)

/**
 * The scout's standing on each of these ranks, which are in the order they're earned, from
 * their [progress] keyed by badge or rank ID. Every screen that shows a rank's status asks this,
 * so they agree.
 *
 * A rank is earned once it's complete ([completion]) and the rank below it is earned, or once
 * it, or a rank above it, is marked earned on a prior date. So the earned ranks are always the
 * lowest ones, and the lowest rank that isn't earned is in progress. Nothing about it is stored,
 * so unmarking a rank undoes what its mark counted.
 */
fun List<Rank>.standings(progress: Map<String, BadgeProgressDetails>): List<RankStanding> {
    val isMarked = { rank: Rank -> progress[rank.id]?.badge?.completedOnPriorDate != null }
    val highestMarked = indexOfLast(isMarked)
    var belowEarned = true
    return mapIndexed { index, rank ->
        val rankProgress = progress[rank.id]
        val version = rank.requirementsVersionFor(rankProgress)
        // Checked first, because a rank marked earned on a prior date needs no version.
        val complete = isMarked(rank) || version?.let { rankProgress?.completion(it) } != null
        val earned = index <= highestMarked || (complete && belowEarned)
        val status = when {
            earned -> RankStatus.Earned
            belowEarned -> RankStatus.InProgress
            else -> RankStatus.NotEarned
        }
        belowEarned = earned
        RankStanding(
            rank = rank,
            status = status,
            // One not started has nothing done, which shows only while it's in progress.
            fractionDone = when {
                earned || version == null -> null
                rankProgress != null -> rankProgress.fractionDone(version)
                status == RankStatus.InProgress -> 0f
                else -> null
            },
            // Not marked itself, so a rank above it is.
            earnedWith = if (earned && !complete) subList(index + 1, size).first(isMarked) else null
        )
    }
}
