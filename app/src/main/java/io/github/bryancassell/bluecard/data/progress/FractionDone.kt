package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion

/**
 * How much of this requirement is done, from 0 to 1, from the scout's recorded progress and
 * tracker entries (each keyed by requirement number). It's 1 once the requirement is complete,
 * as [completion] decides, and gives partial credit before then:
 *
 * - One with children has a part for each child it needs, plus one for its
 *   [own work][Requirement.ownWork], if any. Each needed child counts by how much of it is done;
 *   when only some are needed, the furthest along of them count. Its tracker, if any, doesn't
 *   count, as it doesn't for completion.
 * - One without children but with a tracker with a fixed number of rows counts its filled rows.
 * - Any other requirement is marked complete by hand ([isMarkedByHand]), so it has no parts.
 */
fun Requirement.fractionDone(
    progress: Map<String, RequirementProgress>,
    trackerEntries: Map<String, List<TrackerEntry>>
): Float {
    if (completion(progress, trackerEntries) != null) return 1f
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

        else -> 0f
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

/**
 * How much of this badge is done, from 0 to 1, from the scout's [progress] on it (null if it
 * hasn't been started), while its [status] is in progress. Null otherwise, and for a badge on a
 * requirements version missing from the catalog, which can't be measured. Every screen that shows
 * the badge's progress bar asks this, so they agree on when it shows.
 */
fun MeritBadge.fractionDoneWhileInProgress(progress: BadgeProgressDetails?): Float? {
    if (progress == null || status(progress) != BadgeStatus.InProgress) return null
    return requirementsVersionFor(progress)?.let { progress.fractionDone(it) }
}

/** 1 if the scout marked requirement [number] complete, otherwise 0. */
private fun Map<String, RequirementProgress>.markedDone(number: String): Float =
    if (get(number)?.completed == true) 1f else 0f
