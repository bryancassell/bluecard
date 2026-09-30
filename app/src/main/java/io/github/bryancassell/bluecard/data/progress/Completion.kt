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
 * Whether the scout marks this requirement complete by hand, on its page. A requirement with
 * children is complete once enough of them are instead, and one with a tracker with a fixed
 * number of rows once every row is filled in.
 */
val Requirement.isMarkedByHand: Boolean get() = children.isEmpty() && tracker?.rowCount == null

/**
 * Whether a requirement is complete, from the scout's recorded progress and tracker entries
 * (each keyed by requirement number), or null if it isn't.
 *
 * A requirement with children is complete when enough of them are: all of them, or its
 * `requiredCount`. Its date is when the last child it needed was completed. One with a tracker
 * with a fixed number of rows is complete when every row is filled in, on the date the last of
 * them was first saved. Any other requirement is complete when the scout marked it complete
 * ([isMarkedByHand]).
 */
fun Requirement.completion(
    progress: Map<String, RequirementProgress>,
    trackerEntries: Map<String, List<TrackerEntry>>
): Completion? {
    val rowCount = tracker?.rowCount
    return when {
        children.isNotEmpty() -> childrenCompletion(progress, trackerEntries)
        rowCount != null -> rowsCompletion(trackerEntries[number].orEmpty(), rowCount)
        else -> progress[number]?.takeIf { it.completed }?.let { Completion(it.completedDate) }
    }
}

private fun Requirement.childrenCompletion(
    progress: Map<String, RequirementProgress>,
    trackerEntries: Map<String, List<TrackerEntry>>
): Completion? {
    val needed = requiredCount ?: children.size
    val completed = children.mapNotNull { it.completion(progress, trackerEntries) }
    if (completed.size < needed) return null
    // With more complete children than needed, it was complete once the earliest
    // `needed` of them were done.
    val dates = completed.mapNotNull { it.date }.sorted()
    return Completion(if (dates.size >= needed) dates[needed - 1] else null)
}

/** Complete once each of the [rowCount] rows has an entry, on the latest date one was added. */
private fun rowsCompletion(entries: List<TrackerEntry>, rowCount: Int): Completion? {
    val filled = filledRows(entries, rowCount).values
    if (filled.size < rowCount) return null
    val dates = filled.map { it.addedDate ?: return Completion(null) }
    return Completion(dates.maxOrNull())
}

/**
 * Whether a started badge is complete on [version] of its requirements, or null if it
 * isn't. A badge marked completed on a prior date is complete on that date; otherwise
 * it's complete when all its top-level requirements are, on the last of their dates.
 */
fun BadgeProgressDetails.completion(version: RequirementsVersion): Completion? {
    badge.completedOnPriorDate?.let { return Completion(it) }
    val progress = requirements.associateBy { it.requirementNumber }
    val entries = trackerEntries.groupBy { it.requirementNumber }
    val completed = version.requirements.map { it.completion(progress, entries) ?: return null }
    val dates = completed.map { it.date ?: return Completion(null) }
    return Completion(dates.maxOrNull())
}
