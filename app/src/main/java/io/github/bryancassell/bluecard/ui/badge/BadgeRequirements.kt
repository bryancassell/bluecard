package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import io.github.bryancassell.bluecard.data.progress.requirementsVersionFor

/** A badge, the requirements the scout works on, and what they've recorded against them. */
data class BadgeRequirements(
    val badge: MeritBadge,
    /** The version the badge is worked on ([requirementsVersionFor]). */
    val version: RequirementsVersion,
    /** The scout's recorded requirement progress, keyed by requirement number. */
    val recorded: Map<String, RequirementProgress>
)

/**
 * Badge [badgeId] from this catalog with the scout's [progress] on it, or null if the
 * catalog doesn't have the badge or the version it was started on. Only a catalog edited
 * during development can cause that: released catalogs keep every badge and version they
 * shipped (ARCHITECTURE.md, Merit badge catalog).
 */
fun List<MeritBadge>.badgeRequirements(
    badgeId: String,
    progress: BadgeProgressDetails?
): BadgeRequirements? {
    val badge = find { it.id == badgeId } ?: return null
    val version = badge.requirementsVersionFor(progress) ?: return null
    return BadgeRequirements(
        badge,
        version,
        progress?.requirements.orEmpty().associateBy { it.requirementNumber }
    )
}

/** The requirement numbered [number], at any depth, or null if there is none. */
fun RequirementsVersion.find(number: String): Requirement? = requirements.firstNotNullOfOrNull {
    it.find(number)
}

private fun Requirement.find(number: String): Requirement? =
    if (this.number == number) this else children.firstNotNullOfOrNull { it.find(number) }
