package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import io.github.bryancassell.bluecard.data.progress.completion

/** One requirement as a row: its number, our summary and whether it's complete. */
data class RequirementItem(
    val number: String,
    val summary: String,
    /** How many of its sub-requirements are needed, or null when all of them are. */
    val choice: Choice?,
    val completed: Boolean,
    /**
     * Whether it has sub-requirements or a tracker. Those don't fit in a row, so it opens
     * its own page (ARCHITECTURE.md, Screens).
     */
    val opensDetail: Boolean
)

/** "Do [required] of [of]" sub-requirements. */
data class Choice(val required: Int, val of: Int)

/** [progress] is the scout's recorded progress on the badge, keyed by requirement number. */
fun Requirement.toItem(progress: Map<String, RequirementProgress>) = RequirementItem(
    number = number,
    summary = summary,
    choice = requiredCount?.let { Choice(it, children.size) },
    completed = completion(progress) != null,
    opensDetail = children.isNotEmpty() || tracker != null
)

/** The scout's recorded requirement progress, keyed by requirement number. */
fun BadgeProgressDetails?.requirementProgress(): Map<String, RequirementProgress> =
    this?.requirements.orEmpty().associateBy { it.requirementNumber }

/**
 * The requirements the scout works on: the version the badge was started on, or the newest
 * version if it hasn't been started. Null if it was started on a version that isn't in the
 * catalog, which only a catalog edited during development can cause: released catalogs
 * keep every version they shipped.
 */
fun MeritBadge.requirementsFor(progress: BadgeProgressDetails?): RequirementsVersion? {
    if (progress == null) return requirementVersions.maxBy { it.effectiveDate }
    return requirementVersions.find { it.effectiveDate == progress.badge.requirementsVersion }
}

/** The requirement numbered [number], at any depth, or null if there is none. */
fun RequirementsVersion.find(number: String): Requirement? = requirements.firstNotNullOfOrNull {
    it.find(number)
}

private fun Requirement.find(number: String): Requirement? =
    if (this.number == number) this else children.firstNotNullOfOrNull { it.find(number) }
