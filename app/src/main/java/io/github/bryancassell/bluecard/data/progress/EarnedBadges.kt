package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.MeritBadgesNeeded
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.eagleSlots
import java.time.LocalDate

/**
 * The merit badges the scout has completed, in catalog order, which count toward a rank's
 * requirements that ask for merit badges ([Requirement.meritBadges]).
 */
data class EarnedBadges(val badges: List<EarnedBadge>) {
    /** How far these badges go toward the [needed] merit badges. */
    fun toward(needed: MeritBadgesNeeded): MeritBadgeCredit {
        val eagleRequired = badges.count { it.countsAsEagleRequired(needed) }
        val enough = badges.size >= needed.total && eagleRequired >= needed.eagleRequired
        return MeritBadgeCredit(
            needed = needed,
            completed = badges.size,
            eagleRequired = eagleRequired,
            completion = if (enough) completion(needed) else null
        )
    }

    /**
     * When there were first enough badges for [needed], once there are. As for a requirement's
     * children, that's the day the badges with dates were first enough, or no date if a badge
     * without one is needed.
     */
    private fun completion(needed: MeritBadgesNeeded): Completion {
        var total = 0
        var eagleRequired = 0
        badges.filter { it.completedOn != null }.sortedBy { it.completedOn }.forEach {
            total++
            if (it.countsAsEagleRequired(needed)) eagleRequired++
            if (total >= needed.total && eagleRequired >= needed.eagleRequired) {
                return Completion(it.completedOn)
            }
        }
        return Completion(null)
    }

    companion object {
        /** No badges, which is all a badge's requirements need: none of them ask for badges. */
        val None = EarnedBadges(emptyList())
    }
}

data class EarnedBadge(
    val badge: MeritBadge,
    /** The date the scout completed it on, or null if a requirement it needed has no date. */
    val completedOn: LocalDate?,
    /**
     * Whether it counts as Eagle-required toward a requirement whose Eagle "one of" groups count
     * once ([MeritBadgesNeeded.eagleGroupsCountOnce]): it's Eagle-required, and if it's in a
     * group ([eagleSlots]), it's the first of the group completed. The group's others then count
     * only toward how many badges the scout has completed.
     */
    val countsOnceAsEagleRequired: Boolean
) {
    /** Whether it counts as one of the Eagle-required badges [needed] asks for. */
    fun countsAsEagleRequired(needed: MeritBadgesNeeded): Boolean =
        if (needed.eagleGroupsCountOnce) countsOnceAsEagleRequired else badge.eagleRequired
}

/** How far the scout's completed badges go toward the [needed] merit badges. */
data class MeritBadgeCredit(
    val needed: MeritBadgesNeeded,
    /** How many badges the scout has completed. */
    val completed: Int,
    /** How many of them count as Eagle-required ([EarnedBadge.countsAsEagleRequired]). */
    val eagleRequired: Int,
    /** When there were first enough badges, or null while there aren't. */
    val completion: Completion?
) {
    /** How many more Eagle-required badges the scout needs. */
    val moreEagleRequiredNeeded: Int get() = maxOf(needed.eagleRequired - eagleRequired, 0)

    /**
     * How many more badges the scout needs, [moreEagleRequiredNeeded] of them Eagle-required.
     * More badges that aren't Eagle-required don't help once only Eagle-required ones can.
     */
    val moreNeeded: Int get() = maxOf(needed.total - completed, moreEagleRequiredNeeded)

    /** How many of the badges needed the scout has: the total needed, less [moreNeeded]. */
    val counted: Int get() = needed.total - moreNeeded
}

/**
 * The badges in this catalog the scout has completed, from their [progress] keyed by badge ID,
 * which can have ranks' too.
 */
fun List<MeritBadge>.earnedBadges(progress: Map<String, BadgeProgressDetails>): EarnedBadges {
    // The date each completed badge was completed on, or null if it has none, by ID.
    val completed = mapNotNull { badge ->
        badge.completion(progress[badge.id])?.let { badge.id to it.date }
    }.toMap()
    // The first completed in each group, or the first in catalog order of those completed on
    // the same day or with no date.
    val countsOnceAsEagleRequired = eagleSlots().mapNotNull { slot ->
        slot.filter { it.id in completed }
            .minWithOrNull(compareBy(nullsLast()) { completed[it.id] })
            ?.id
    }.toSet()
    return EarnedBadges(
        filter { it.id in completed }.map {
            EarnedBadge(it, completed[it.id], it.id in countsOnceAsEagleRequired)
        }
    )
}
