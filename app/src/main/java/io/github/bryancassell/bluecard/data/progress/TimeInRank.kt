package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.catalog.Requirement
import java.time.LocalDate

/**
 * The time a rank's requirement asks the scout to spend in the rank below it
 * ([Requirement.monthsInRank]), and when that's up.
 */
data class TimeInRank(
    val months: Int,
    /** The name of the rank below, which the months are counted from. */
    val rankBelow: String,
    val eligibility: Eligibility
) {
    /**
     * When the scout becomes eligible to complete the requirement, or why that isn't known. It's
     * only a guide: the scout checks the requirement off, before or after it.
     */
    sealed interface Eligibility {
        data object RankBelowNotEarned : Eligibility

        /** Such as for a rank counted as earned with a rank above it ([RankStanding.earnedWith]). */
        data object RankBelowHasNoDate : Eligibility

        /** The date the rank below was earned on ([RankStanding.earnedOn]) plus the months. */
        data class From(val date: LocalDate) : Eligibility
    }
}

/**
 * The time [requirement] of rank [rankId] asks for in the rank below, from these standings of
 * every rank, or null if it asks for none.
 */
fun List<RankStanding>.timeInRank(rankId: String, requirement: Requirement): TimeInRank? {
    val months = requirement.monthsInRank ?: return null
    // The catalog gives none to the lowest rank, which has none below it.
    val below = getOrNull(indexOfFirst { it.rank.id == rankId } - 1) ?: return null
    val earnedOn = below.earnedOn
    return TimeInRank(
        months = months,
        rankBelow = below.rank.name,
        eligibility = when {
            // A day the month doesn't have moves to its last: Aug 31 plus 6 months is Feb 28.
            earnedOn != null -> TimeInRank.Eligibility.From(earnedOn.plusMonths(months.toLong()))

            below.status == RankStatus.Earned -> TimeInRank.Eligibility.RankBelowHasNoDate

            else -> TimeInRank.Eligibility.RankBelowNotEarned
        }
    )
}
