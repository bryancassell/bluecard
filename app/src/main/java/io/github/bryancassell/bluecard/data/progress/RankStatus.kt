package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Rank
import java.time.LocalDate

/**
 * How far the scout has got with a rank. Ranks are earned in order, Scout through Eagle, so while
 * the scout can record requirements for any rank, only one is next (#193).
 */
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
     * complete. Null otherwise. The nearest of them, if there's more than one. So a scout who
     * starts using BlueCard as a Life Scout marks only Life (#193).
     */
    val earnedWith: Rank? = null,
    /**
     * The date the rank was earned on, once it's earned: the date the scout marked it earned on,
     * or for one earned from its requirements, the date they were completed on ([completion]).
     * Null for one that isn't earned, one [earnedWith] a rank above it, or one with a
     * requirement it needed completed with no date.
     */
    val earnedOn: LocalDate? = null,
    /**
     * For a rank whose requirements are complete but isn't earned, the rank below it, which has
     * to be earned first. Null otherwise.
     */
    val waitingOn: Rank? = null
)

/**
 * Whether the rank reads as marked earned on a prior date, from the scout's [progress] on it:
 * it's marked itself, or it counts as earned with a rank above it that is ([earnedWith]). Its
 * requirements still needed with no part done then read "Not recorded", as on a marked badge.
 */
fun RankStanding.readsAsMarked(progress: BadgeProgressDetails?): Boolean =
    progress?.badge?.completedOnPriorDate != null || earnedWith != null

/**
 * The scout's standing on each of these ranks, which are in the order they're earned, from
 * their [progress] keyed by badge or rank ID and the badges they've completed ([earnedBadges]),
 * which a rank's requirements that ask for merit badges count. Every screen that shows a rank's
 * status asks this, so they agree.
 *
 * A rank is earned once it's complete ([completion]) and the rank below it is earned, or once
 * it, or a rank above it, is marked earned on a prior date. So the earned ranks are always the
 * lowest ones, and the lowest rank that isn't earned is in progress. Nothing about it is stored,
 * so unmarking a rank undoes what its mark counted.
 */
fun List<Rank>.standings(
    progress: Map<String, BadgeProgressDetails>,
    earnedBadges: EarnedBadges
): List<RankStanding> {
    val isMarked = { rank: Rank -> progress[rank.id]?.badge?.completedOnPriorDate != null }
    val highestMarked = indexOfLast(isMarked)
    var belowEarned = true
    return mapIndexed { index, rank ->
        val rankProgress = progress[rank.id]
        val version = rank.requirementsVersionFor(rankProgress)
        val markedOn = rankProgress?.badge?.completedOnPriorDate
        // Not looked for on a marked rank, which needs no version.
        val completion = if (markedOn ==
            null
        ) {
            version?.let { rankProgress?.completion(it, earnedBadges) }
        } else {
            null
        }
        val complete = markedOn != null || completion != null
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
            // One not started has nothing recorded, though badges can count toward it. Its bar
            // shows only while it's in progress: otherwise one earned badge would put bars on
            // Star, Life and Eagle at once.
            fractionDone = when {
                earned || version == null -> null

                rankProgress != null -> rankProgress.fractionDone(version, earnedBadges)

                status == RankStatus.InProgress ->
                    version.fractionDone(emptyMap(), emptyMap(), earnedBadges)

                else -> null
            },
            // Not marked itself, so a rank above it is.
            earnedWith = if (earned &&
                !complete
            ) {
                subList(index + 1, size).first(isMarked)
            } else {
                null
            },
            earnedOn = if (earned) markedOn ?: completion?.date else null,
            // Not the lowest rank, which is earned once complete.
            waitingOn = if (complete && !earned) this[index - 1] else null
        )
    }
}

/**
 * The ranks earned with the scout's progress [now] that wouldn't be with the progress [after] a
 * change, such as clearing a badge or a rank, in the order they're earned. Both are keyed by
 * badge or rank ID. The [badges] completed in each count toward ranks that ask for merit badges,
 * so changing a badge can change which ranks are earned.
 */
fun List<Rank>.noLongerEarned(
    badges: List<MeritBadge>,
    now: Map<String, BadgeProgressDetails>,
    after: Map<String, BadgeProgressDetails>
): List<Rank> = standings(now, badges.earnedBadges(now))
    .zip(standings(after, badges.earnedBadges(after)))
    .filter { (standingNow, standingAfter) ->
        standingNow.status == RankStatus.Earned && standingAfter.status != RankStatus.Earned
    }
    .map { (standingNow) -> standingNow.rank }
