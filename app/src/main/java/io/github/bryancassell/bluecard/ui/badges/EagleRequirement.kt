package io.github.bryancassell.bluecard.ui.badges

import android.icu.text.ListFormatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.ui.stringsLocale
import java.text.Collator
import java.util.Locale

/** How a badge counts toward Eagle Scout. */
sealed interface EagleRequirement {
    /** Required on its own. */
    data object Required : EagleRequirement

    /**
     * One of a group of alternatives, such as Cycling, Hiking and Swimming. [badgeNames]
     * are the group's badges, this one included, in [badgeNameOrder].
     */
    data class OneOf(val badgeNames: List<String>) : EagleRequirement
}

/**
 * The order badges are listed in. Badge names are English whatever the device language, so
 * they're sorted by English rules: an accented letter sorts with its base letter, and case
 * only breaks ties.
 */
fun badgeNameOrder(): Comparator<MeritBadge> =
    compareBy(Collator.getInstance(Locale.ENGLISH), MeritBadge::name)

/** Maps each Eagle group in this catalog to the names of its badges, in [badgeNameOrder]. */
fun List<MeritBadge>.eagleGroups(): Map<String, List<String>> {
    val byName = badgeNameOrder()
    // Only the few badges in groups are sorted.
    return mapNotNull { badge -> badge.eagleGroup?.let { it to badge } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, badges) -> badges.sortedWith(byName).map { it.name } }
}

/** How this badge counts toward Eagle Scout, or null if it doesn't. */
fun MeritBadge.eagleRequirement(eagleGroups: Map<String, List<String>>): EagleRequirement? {
    if (!eagleRequired) return null
    // The catalog is written in stages; while a group has only this badge so far, it's
    // shown as required on its own.
    val group = eagleGroup?.let(eagleGroups::get)?.takeIf { it.size > 1 }
        ?: return EagleRequirement.Required
    return EagleRequirement.OneOf(group)
}

/**
 * Formats lists of badge names in the language of the surrounding string, not the device's,
 * which the app may not have strings for.
 */
@Composable
fun rememberBadgeNameListFormatter(): ListFormatter {
    val locale = stringsLocale()
    return remember(locale) { ListFormatter.getInstance(locale) }
}

/** How [requirement] is shown, such as "Eagle-required (one of Cycling, Hiking, and Swimming)". */
@Composable
fun eagleRequirementLabel(requirement: EagleRequirement, listFormatter: ListFormatter): String =
    when (requirement) {
        EagleRequirement.Required -> stringResource(R.string.badges_eagle_required)

        is EagleRequirement.OneOf -> stringResource(
            R.string.badges_eagle_required_one_of,
            listFormatter.format(requirement.badgeNames)
        )
    }
