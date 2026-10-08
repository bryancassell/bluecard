package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition

/**
 * How much of this requirement is done, from 0 to 1, from the scout's recorded progress and
 * tracker entries (each keyed by requirement number). It's 1 once the requirement is complete,
 * as [completion] decides, and gives partial credit before then:
 *
 * - One with children has a part for each child it needs, plus one for its
 *   [own work][Requirement.ownWork], if any. Each needed child counts by how much of it is done;
 *   when only some are needed, the furthest along of them count. Its tracker, if any, doesn't
 *   count, as it doesn't for completion.
 * - One without children but with a tracker with a fixed number of rows has a part for each row,
 *   plus one for its own work, if any.
 * - One without children but with a log that asks for a number of rows
 *   ([TrackerDefinition.rowsNeeded]) has a part for each row it asks for, which rows past that
 *   number don't add to, plus one for checking it off. Checking it off completes it, so its rows
 *   alone never make it all done.
 * - A rank's requirement that asks for merit badges counts the badges needed that the scout's
 *   [earnedBadges] give it ([MeritBadgeCredit.counted]).
 * - Any other requirement is marked complete by hand ([isMarkedByHand]), so it has no parts.
 */
fun Requirement.fractionDone(
    progress: Map<String, RequirementProgress>,
    trackerEntries: Map<String, List<TrackerEntry>>,
    earnedBadges: EarnedBadges = EarnedBadges.None
): Float {
    if (completion(progress, trackerEntries, earnedBadges) != null) return 1f
    val rowCount = tracker?.rowCount
    val rowsNeeded = tracker?.rowsNeeded
    return when {
        meritBadges != null ->
            earnedBadges.toward(meritBadges).counted.toFloat() / meritBadges.total

        children.isNotEmpty() -> {
            val needed = requiredCount ?: children.size
            val childrenDone = children.map {
                it.fractionDone(progress, trackerEntries, earnedBadges)
            }
                .sortedDescending()
                .take(needed)
                .sum()
            withOwnWork(childrenDone, needed, progress)
        }

        rowCount != null -> withOwnWork(
            filledRows(trackerEntries[number].orEmpty(), rowCount).size.toFloat(),
            rowCount,
            progress
        )

        rowsNeeded != null ->
            minOf(trackerEntries[number].orEmpty().size, rowsNeeded).toFloat() / (rowsNeeded + 1)

        else -> 0f
    }
}

/**
 * How much of a started badge or rank is done on [version] of its requirements, from 0 to 1: the
 * average of its top-level requirements' [fractionDone], so each weighs the same. One marked
 * completed on a prior date is all done. A rank's requirements that ask for merit badges count
 * the scout's [earnedBadges].
 */
fun BadgeProgressDetails.fractionDone(
    version: RequirementsVersion,
    earnedBadges: EarnedBadges = EarnedBadges.None
): Float {
    if (badge.completedOnPriorDate != null) return 1f
    return version.fractionDone(
        requirements.associateBy { it.requirementNumber },
        trackerEntries.groupBy { it.requirementNumber },
        earnedBadges
    )
}

/**
 * How much of a badge or rank on this version of its requirements is done, from 0 to 1, from
 * the scout's recorded progress and tracker entries (each keyed by requirement number) and
 * [earnedBadges], as [BadgeProgressDetails.fractionDone] works it out.
 */
fun RequirementsVersion.fractionDone(
    progress: Map<String, RequirementProgress>,
    trackerEntries: Map<String, List<TrackerEntry>>,
    earnedBadges: EarnedBadges
): Float = requirements.map { it.fractionDone(progress, trackerEntries, earnedBadges) }
    .average()
    .toFloat()

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

/**
 * How much is done with [done] of its [parts] done, and one more part for its
 * [own work][Requirement.ownWork], if it has any.
 */
private fun Requirement.withOwnWork(
    done: Float,
    parts: Int,
    progress: Map<String, RequirementProgress>
): Float = if (ownWork == null) done / parts else (done + progress.markedDone(number)) / (parts + 1)

/** 1 if the scout marked requirement [number] complete, otherwise 0. */
private fun Map<String, RequirementProgress>.markedDone(number: String): Float =
    if (get(number)?.completed == true) 1f else 0f
