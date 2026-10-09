package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.catalog.ColumnTotal
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition
import java.math.BigDecimal

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
 * - One without children but with a log with [totals][TrackerColumn.total] has a part for each
 *   unit of the amount it asks for ([amountDone]), such as each of Star 4's 6 hours, which units
 *   past that amount don't add to, plus one for checking it off, as for a log that asks for a
 *   number of rows. Totals added nothing until checked off, while Second Class 4's "6 of 10
 *   animals" added 6 of 11 parts (#292). Counting each unit, as for rows, keeps the two alike,
 *   though checking it off weighs more on a small amount: it's half of Tenderfoot 7b's one hour.
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

        else -> tracker?.loggedTowardNumber(trackerEntries[number].orEmpty())
            ?.let { (done, needed) -> beforeCheckOff(done.toFloat(), needed) }
            ?: 0f
    }
}

/**
 * Whether this requirement, which the scout marks complete by hand ([isMarkedByHand]), has the
 * number of rows or the amount it asks for in its log ([TrackerDefinition.rowsNeeded],
 * [TrackerColumn.total]), such as Tenderfoot 6b's 30 days or Star 4's 6 hours, as [fractionDone]
 * counts them. They don't complete it: the scout checks it off once all of it is done. Life 4 has
 * its 6 hours once 3 of them are on conservation ([amountDone]). "6 of 6 hours" reads as finished
 * as "30 of 30 days" does (#293), but above "2 of 3 conservation hours" it doesn't, so it waits
 * for every total.
 */
fun Requirement.hasEnoughLogged(trackerEntries: Map<String, List<TrackerEntry>>): Boolean {
    if (!isMarkedByHand) return false
    val (done, needed) = tracker?.loggedTowardNumber(trackerEntries[number].orEmpty())
        ?: return false
    return done >= needed.toBigDecimal()
}

/**
 * How much of the number of rows or the amount its requirement asks for this log's [entries]
 * make up ([amountDone]), and that number, or null if it asks for neither.
 */
private fun TrackerDefinition.loggedTowardNumber(
    entries: List<TrackerEntry>
): Pair<BigDecimal, Int>? = rowsNeeded?.let { entries.size.toBigDecimal() to it }
    ?: amountDone(entries)

/**
 * How much is done with [done] of the [needed] rows or units a log's requirement asks for, which
 * any past that number don't add to, and one more part for checking it off.
 */
private fun beforeCheckOff(done: Float, needed: Int): Float =
    minOf(done, needed.toFloat()) / (needed + 1)

/**
 * How much of the amount its requirement asks for this log's [entries] make up, such as 4.5 of
 * 6 hours, and the amount, or null if it has no [total][TrackerColumn.total]. A log has one
 * total, and at most one more that's [part of][ColumnTotal.partOf] it (docs/catalog.md). Then the
 * total counts no more than the part and the rest of its amount: Life 4's 6 hours with no
 * conservation hours count as 3, as its 3 conservation hours are still to do. That keeps the bar
 * in step with the hours still to do: counting only the 6 hours would leave it still while the
 * scout did the conservation hours, and counting both totals would count a conservation hour
 * twice.
 */
private fun TrackerDefinition.amountDone(entries: List<TrackerEntry>): Pair<BigDecimal, Int>? {
    val totals = columns.mapNotNull { column -> column.total?.let { column to it } }
    val (column, total) = totals.find { it.second.partOf == null } ?: return null
    val done = column.sumOver(entries)
    val (partColumn, partTotal) = totals.find { it.second.partOf == column.id }
        ?: return done to total.needed
    val rest = (total.needed - partTotal.needed).toBigDecimal()
    return minOf(done, partColumn.sumOver(entries) + rest) to total.needed
}

/**
 * How much of a started badge or rank is done on [version] of its requirements, from 0 to 1: the
 * average of its top-level requirements' [fractionDone], so each weighs the same. One marked
 * completed on a prior date is all done. A rank's requirements that ask for merit badges count
 * the scout's [earnedBadges].
 *
 * Counting only complete top-level requirements would leave the bar still while the scout works
 * through a big one, such as First Aid 3's 17 parts, and counting every part the same would let
 * one big requirement outweigh the rest (#159).
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
