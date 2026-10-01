package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import io.github.bryancassell.bluecard.data.progress.TrackerEntry
import io.github.bryancassell.bluecard.data.progress.completion
import io.github.bryancassell.bluecard.data.progress.requirementsVersionFor

/** A badge, the requirements the scout works on, and what they've recorded against them. */
data class BadgeRequirements(
    val badge: MeritBadge,
    /** The version the badge is worked on ([requirementsVersionFor]). */
    val version: RequirementsVersion,
    /** The scout's recorded requirement progress, keyed by requirement number. */
    val recorded: Map<String, RequirementProgress>,
    /** The scout's tracker entries, keyed by requirement number. */
    val trackerEntries: Map<String, List<TrackerEntry>>
) {
    /**
     * [requirement] of this badge as a row. [partOfCompleted] is whether a requirement it's part
     * of is complete.
     */
    fun item(requirement: Requirement, partOfCompleted: Boolean = false) =
        requirement.toItem(recorded, trackerEntries, partOfCompleted)

    /** Whether a requirement that the one numbered [number] is part of, at any depth, is complete. */
    fun partOfCompleted(number: String): Boolean =
        version.ancestorsOf(number).any { it.completion(recorded, trackerEntries) != null }
}

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
        progress?.requirements.orEmpty().associateBy { it.requirementNumber },
        progress?.trackerEntries.orEmpty().groupBy { it.requirementNumber }
    )
}

/** The requirement numbered [number], at any depth, or null if there is none. */
fun RequirementsVersion.find(number: String): Requirement? = requirements.firstNotNullOfOrNull {
    it.find(number)
}

private fun Requirement.find(number: String): Requirement? =
    if (this.number == number) this else children.firstNotNullOfOrNull { it.find(number) }

/**
 * The requirements that the one numbered [number] is part of, outermost first: none for a
 * top-level requirement or one not in this version.
 */
private fun RequirementsVersion.ancestorsOf(number: String): List<Requirement> =
    requirements.firstNotNullOfOrNull { it.pathTo(number) }?.dropLast(1).orEmpty()

/** This requirement down to the one numbered [number], or null if that isn't in it. */
private fun Requirement.pathTo(number: String): List<Requirement>? = if (this.number == number) {
    listOf(this)
} else {
    children.firstNotNullOfOrNull { it.pathTo(number) }?.let { listOf(this) + it }
}
