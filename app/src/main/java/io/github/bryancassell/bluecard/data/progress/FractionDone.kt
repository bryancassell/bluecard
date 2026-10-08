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
 *   number of rows.
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

        else -> tracker?.amountDone(trackerEntries[number].orEmpty())
            ?.let { (done, needed) -> done.toFloat() / (needed + 1) }
            ?: 0f
    }
}

/**
 * How much of the amount its requirement asks for this log's [entries] make up, and that amount,
 * such as 4.5 of 6 hours, or null if it has no [totals][TrackerColumn.total]. Each total counts
 * its values added up, to at most the amount it needs. One that's [part of][ColumnTotal.partOf]
 * another isn't counted itself, but that one counts no more than the part, to at most what the
 * part needs, and the rest of its amount: Life 4's 6 hours with no conservation hours count as 3
 * of 6, as its 3 conservation hours are still to do.
 */
private fun TrackerDefinition.amountDone(entries: List<TrackerEntry>): Pair<BigDecimal, Int>? {
    val totals = columns.mapNotNull { column -> column.total?.let { column to it } }
    val wholes = totals.filter { (_, total) -> total.partOf == null }
    if (wholes.isEmpty()) return null
    val done = wholes.sumOf { (column, total) ->
        val counted = column.counted(total, entries)
        val part = totals.find { (_, partTotal) -> partTotal.partOf == column.id }
        if (part == null) {
            counted
        } else {
            val (partColumn, partTotal) = part
            val rest = (total.needed - partTotal.needed).toBigDecimal()
            minOf(counted, partColumn.counted(partTotal, entries) + rest)
        }
    }
    return done to wholes.sumOf { (_, total) -> total.needed }
}

/** This column's values added up over [entries], to at most the amount its [total] needs. */
private fun TrackerColumn.counted(total: ColumnTotal, entries: List<TrackerEntry>): BigDecimal =
    minOf(sumOver(entries), total.needed.toBigDecimal())

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
