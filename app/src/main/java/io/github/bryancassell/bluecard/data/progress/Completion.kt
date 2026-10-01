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
 * children is complete once enough of them are instead (and the scout marks its
 * [own work][Requirement.ownWork], if any, by hand), and one with a tracker with a fixed number
 * of rows once every row is filled in.
 */
val Requirement.isMarkedByHand: Boolean get() = children.isEmpty() && tracker?.rowCount == null

/**
 * Whether a requirement is complete, from the scout's recorded progress and tracker entries
 * (each keyed by requirement number), or null if it isn't.
 *
 * A requirement with children is complete when enough of them are: all of them, or its
 * `requiredCount`, even if it also has a tracker. Its date is when the last child it needed was
 * completed. One with [own work][Requirement.ownWork] also needs the scout to mark that complete,
 * and its date is the later of the two. One without children but with a tracker with a fixed
 * number of rows is complete when every row is filled in, on the date the last of them was first
 * saved. Any other requirement is complete when the scout marked it complete ([isMarkedByHand]).
 */
fun Requirement.completion(
    progress: Map<String, RequirementProgress>,
    trackerEntries: Map<String, List<TrackerEntry>>
): Completion? {
    val rowCount = tracker?.rowCount
    return when {
        children.isNotEmpty() -> {
            val byChildren = childrenCompletion(progress, trackerEntries) ?: return null
            if (ownWork == null) {
                byChildren
            } else {
                latestOf(listOf(byChildren, markedCompletion(progress) ?: return null))
            }
        }

        rowCount != null -> rowsCompletion(trackerEntries[number].orEmpty(), rowCount)

        else -> markedCompletion(progress)
    }
}

/**
 * Whether enough of this requirement's children are complete that it needs no more of them,
 * even if its [own work][Requirement.ownWork] isn't done yet. Its other children are then not
 * needed.
 */
fun Requirement.hasEnoughChildren(
    progress: Map<String, RequirementProgress>,
    trackerEntries: Map<String, List<TrackerEntry>>
): Boolean = children.isNotEmpty() && childrenCompletion(progress, trackerEntries) != null

/**
 * Whether any part of this requirement is done, complete or not: it or its
 * [own work][Requirement.ownWork] is marked complete, a row of its tracker is filled in, or the
 * same is true of a requirement under it at any depth. A complete requirement under it has a part
 * done too, so it counts.
 */
fun Requirement.hasPartDone(
    progress: Map<String, RequirementProgress>,
    trackerEntries: Map<String, List<TrackerEntry>>
): Boolean = ((isMarkedByHand || ownWork != null) && progress[number]?.completed == true) ||
    hasTrackerRows(trackerEntries[number].orEmpty()) ||
    children.any { it.hasPartDone(progress, trackerEntries) }

/** Whether the [entries] recorded for it fill in a row of its tracker. */
private fun Requirement.hasTrackerRows(entries: List<TrackerEntry>): Boolean {
    val rowCount = tracker?.rowCount
    return when {
        tracker == null -> false
        rowCount != null -> filledRows(entries, rowCount).isNotEmpty()
        else -> entries.isNotEmpty()
    }
}

/** Complete once the scout marked it complete, on the date they gave. */
private fun Requirement.markedCompletion(progress: Map<String, RequirementProgress>): Completion? =
    progress[number]?.takeIf { it.completed }?.let { Completion(it.completedDate) }

private fun Requirement.childrenCompletion(
    progress: Map<String, RequirementProgress>,
    trackerEntries: Map<String, List<TrackerEntry>>
): Completion? {
    val needed = neededCount
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
    return latestOf(filled.map { Completion(it.addedDate) })
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
    return latestOf(version.requirements.map { it.completion(progress, entries) ?: return null })
}

/** Complete on the latest date of [completions], or with no date if one of them has none. */
private fun latestOf(completions: List<Completion>): Completion =
    Completion(completions.map { it.date ?: return Completion(null) }.maxOrNull())
