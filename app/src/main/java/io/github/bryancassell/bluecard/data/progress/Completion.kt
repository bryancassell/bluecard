package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import java.time.LocalDate

/**
 * A completed requirement or badge, with the date it was completed on, or null when a
 * requirement that counts toward it has no date.
 */
data class Completion(val date: LocalDate?)

/**
 * Whether a requirement is complete, from the scout's recorded progress (keyed by
 * requirement number), or null if it isn't.
 *
 * A requirement without children is complete when the scout marked it complete. A
 * requirement with children is complete when enough of them are: all of them, or its
 * `requiredCount`. Its date is when the last child it needed was completed.
 */
fun Requirement.completion(progress: Map<String, RequirementProgress>): Completion? {
    if (children.isEmpty()) {
        val recorded = progress[number]
        return if (recorded?.completed == true) Completion(recorded.completedDate) else null
    }
    val needed = requiredCount ?: children.size
    val completed = children.mapNotNull { it.completion(progress) }
    if (completed.size < needed) return null
    // With more complete children than needed, it was complete once the earliest
    // `needed` of them were done.
    val dates = completed.mapNotNull { it.date }.sorted()
    return Completion(if (dates.size >= needed) dates[needed - 1] else null)
}

/**
 * Whether a started badge is complete on [version] of its requirements, or null if it
 * isn't. A badge marked completed on a prior date is complete on that date; otherwise
 * it's complete when all its top-level requirements are, on the last of their dates.
 */
fun BadgeProgressDetails.completion(version: RequirementsVersion): Completion? {
    badge.completedOnPriorDate?.let { return Completion(it) }
    val progress = requirements.associateBy { it.requirementNumber }
    val completed = version.requirements.map { it.completion(progress) ?: return null }
    val dates = completed.map { it.date ?: return Completion(null) }
    return Completion(dates.maxOrNull())
}
