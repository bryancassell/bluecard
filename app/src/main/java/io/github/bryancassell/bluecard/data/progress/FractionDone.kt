package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion

/**
 * How much of this requirement is done, from 0 to 1, from the scout's recorded progress and
 * tracker entries (each keyed by requirement number). It's 1 once the requirement is complete
 * ([completion]), and gives partial credit before then:
 *
 * - One with children has a part for each child it needs, plus one for its
 *   [own work][Requirement.ownWork], if any. Each needed child counts by how much of it is done;
 *   when only some are needed, the furthest along of them count. Its tracker, if any, doesn't
 *   count, as it doesn't for completion.
 * - One without children but with a tracker with a fixed number of rows counts its filled rows.
 * - Any other requirement is done once the scout marked it complete ([isMarkedByHand]).
 */
fun Requirement.fractionDone(
    progress: Map<String, RequirementProgress>,
    trackerEntries: Map<String, List<TrackerEntry>>
): Float {
    val rowCount = tracker?.rowCount
    return when {
        children.isNotEmpty() -> {
            val needed = requiredCount ?: children.size
            val childrenDone = children.map { it.fractionDone(progress, trackerEntries) }
                .sortedDescending()
                .take(needed)
                .sum()
            if (ownWork == null) {
                childrenDone / needed
            } else {
                (childrenDone + progress.markedDone(number)) / (needed + 1)
            }
        }

        rowCount != null ->
            filledRows(trackerEntries[number].orEmpty(), rowCount).size.toFloat() / rowCount

        else -> progress.markedDone(number)
    }
}

/**
 * How much of a started badge is done on [version] of its requirements, from 0 to 1: the
 * average of its top-level requirements' [fractionDone], so each weighs the same. A badge marked
 * completed on a prior date is all done.
 */
fun BadgeProgressDetails.fractionDone(version: RequirementsVersion): Float {
    if (badge.completedOnPriorDate != null) return 1f
    val progress = requirements.associateBy { it.requirementNumber }
    val entries = trackerEntries.groupBy { it.requirementNumber }
    return version.requirements.map { it.fractionDone(progress, entries) }.average().toFloat()
}

/** 1 if the scout marked requirement [number] complete, otherwise 0. */
private fun Map<String, RequirementProgress>.markedDone(number: String): Float =
    if (get(number)?.completed == true) 1f else 0f
