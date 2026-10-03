package io.github.bryancassell.bluecard.ui.badges

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.badgeNameOrder
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.fractionDoneWhileInProgress
import io.github.bryancassell.bluecard.data.progress.status

/**
 * A badge in the catalog as lists show it, on Badges and on Home: with how it counts toward
 * Eagle Scout, which depends on the rest of the catalog.
 */
data class ListedBadge(val badge: MeritBadge, val eagle: EagleRequirement?) {
    /**
     * This badge's row, with the scout's status on it and, while it's in progress, how much of
     * it is done, from their [progress] on it (null if it hasn't been started).
     */
    fun toListItem(progress: BadgeProgressDetails?) = BadgeListItem(
        id = badge.id,
        name = badge.name,
        eagle = eagle,
        status = badge.status(progress),
        fractionDone = badge.fractionDoneWhileInProgress(progress)
    )
}

/**
 * This catalog's badges in the order lists show them, [badgeNameOrder]. It depends only on the
 * catalog, so screens work it out once rather than on every progress change.
 */
fun List<MeritBadge>.inListOrder(): List<ListedBadge> {
    val eagleGroups = eagleGroups()
    return sortedWith(badgeNameOrder()).map { ListedBadge(it, it.eagleRequirement(eagleGroups)) }
}
