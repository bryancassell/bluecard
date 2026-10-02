package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import io.github.bryancassell.bluecard.data.progress.TrackerEntry
import io.github.bryancassell.bluecard.data.progress.hasEnoughChildren
import io.github.bryancassell.bluecard.data.progress.requirementsVersionFor

/** A badge, the requirements the scout works on, and what they've recorded against them. */
data class BadgeRequirements(
    val badge: MeritBadge,
    /** The version the badge is worked on ([requirementsVersionFor]). */
    val version: RequirementsVersion,
    /** The scout's recorded requirement progress, keyed by requirement number. */
    val recorded: Map<String, RequirementProgress>,
    /** The scout's tracker entries, keyed by requirement number. */
    val trackerEntries: Map<String, List<TrackerEntry>>,
    /** Whether the scout marked the badge completed on a prior date. */
    val completedOnPriorDate: Boolean = false
) {
    /** [requirement] of this badge as a row. */
    fun item(requirement: Requirement) = requirement.toItem(
        recorded,
        trackerEntries,
        partOfHasEnough = partOfHasEnough(requirement.number),
        badgeCompletedOnPriorDate = completedOnPriorDate
    )

    /**
     * Whether the scout has recorded anything that shows for a requirement numbered in
     * [numbers]: its completion, comment or tracker entries.
     */
    fun hasRecorded(numbers: Collection<String>): Boolean = numbers.any { number ->
        recorded[number]?.let { it.completed || it.comment != null } == true ||
            number in trackerEntries
    }

    /**
     * Whether a requirement that the one numbered [number] is part of, at any depth, has enough
     * complete sub-requirements ([hasEnoughChildren]).
     */
    private fun partOfHasEnough(number: String): Boolean = version.pathTo(number).orEmpty()
        .dropLast(1)
        .any { it.hasEnoughChildren(recorded, trackerEntries) }
}

/**
 * Badge [badgeId] from this catalog with the scout's [progress] on it, or null if the
 * catalog doesn't have the badge or the version it was started on. Only a catalog edited
 * during development can cause that: released catalogs keep every badge and version they
 * shipped (ARCHITECTURE.md, Requirement versions).
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
        progress?.trackerEntries.orEmpty().groupBy { it.requirementNumber },
        progress?.badge?.completedOnPriorDate != null
    )
}

/** The requirement numbered [number], at any depth, or null if there is none. */
fun RequirementsVersion.find(number: String): Requirement? = pathTo(number)?.last()

/** The numbers of this requirement and of every one under it, at any depth. */
fun Requirement.numbersWithin(): List<String> =
    listOf(number) + children.flatMap { it.numbersWithin() }

/**
 * The requirements from the top level down to the one numbered [number], or null if there is
 * none.
 */
private fun RequirementsVersion.pathTo(number: String): List<Requirement>? =
    requirements.firstNotNullOfOrNull { it.pathTo(number) }

/** This requirement down to the one numbered [number], or null if that isn't in it. */
private fun Requirement.pathTo(number: String): List<Requirement>? = if (this.number == number) {
    listOf(this)
} else {
    children.firstNotNullOfOrNull { it.pathTo(number) }?.let { listOf(this) + it }
}
