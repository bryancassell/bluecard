package io.github.bryancassell.bluecard.ui.badges

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.progress.BadgeStatus

/**
 * A badge in the catalog as lists show it, on Badges and on Home: with how it counts toward
 * Eagle Scout, which depends on the rest of the catalog.
 */
data class ListedBadge(val badge: MeritBadge, val eagle: EagleRequirement?) {
    /** This badge's row, with the scout's [status] on it. */
    fun toListItem(status: BadgeStatus) = BadgeListItem(
        id = badge.id,
        name = badge.name,
        eagle = eagle,
        status = status
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
