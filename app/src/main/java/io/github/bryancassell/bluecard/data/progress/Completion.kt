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
 * children is complete once enough of them are instead, one with a tracker with a fixed number
 * of rows once every row is filled in, and one that asks for merit badges once the scout has
 * completed enough of them. The scout marks the [own work][Requirement.ownWork] of either of the
 * first two, if any, by hand.
 */
val Requirement.isMarkedByHand: Boolean
    get() = children.isEmpty() && tracker?.rowCount == null && meritBadges == null

/**
 * Whether this requirement is complete once every row of its tracker is filled in: it has no
 * children or [own work][Requirement.ownWork], and its tracker has a fixed number of rows. The
 * scout can give the date it was completed on, as for one they mark by hand ([completion]).
 */
val Requirement.completesFromRows: Boolean
    get() = children.isEmpty() && ownWork == null && tracker?.rowCount != null

/**
 * Whether a requirement is complete, from the scout's recorded progress and tracker entries
 * (each keyed by requirement number), or null if it isn't. A rank's requirement that asks for
 * merit badges ([Requirement.meritBadges]) is complete once the scout's [earnedBadges] are
 * enough ([EarnedBadges.toward]), whatever they recorded for it.
 *
 * A requirement with children is complete when enough of them are: all of them, or its
 * `requiredCount`, even if it also has a tracker. Its date is when the last child it needed was
 * completed. One with [own work][Requirement.ownWork] also needs the scout to mark that complete,
 * and its date is the later of the two. One without children but with a tracker with a fixed
 * number of rows is complete when every row is filled in ([completesFromRows]). Its date is the one
 * the scout gave, if they gave one, or else the date the last of its rows was first saved. One
 * that also has own work needs the scout to mark that complete too, and its date is the one they
 * gave the own work, since the rows' dates are only when they were typed in. Any other
 * requirement is complete when the scout marked it complete ([isMarkedByHand]).
 */
fun Requirement.completion(
    progress: Map<String, RequirementProgress>,
    trackerEntries: Map<String, List<TrackerEntry>>,
    earnedBadges: EarnedBadges = EarnedBadges.None
): Completion? {
    val rowCount = tracker?.rowCount
    return when {
        meritBadges != null -> earnedBadges.toward(meritBadges).completion

        children.isNotEmpty() -> {
            val byChildren =
                childrenCompletion(progress, trackerEntries, earnedBadges) ?: return null
            if (ownWork == null) {
                byChildren
            } else {
                latestOf(listOf(byChildren, markedCompletion(progress) ?: return null))
            }
        }

        rowCount != null -> {
            val byRows = rowsCompletion(trackerEntries[number].orEmpty(), rowCount) ?: return null
            if (ownWork == null) {
                // The scout gives the date as they mark a requirement by hand, but it doesn't
                // complete this one.
                markedCompletion(progress) ?: byRows
            } else {
                markedCompletion(progress)
            }
        }

        else -> markedCompletion(progress)
    }
}

/**
 * When every row of this requirement's tracker was filled in, for one that [completesFromRows]:
 * the latest date one of them was first saved. It's the requirement's completion date unless the
 * scout gave one. Null until every row is filled in, if one of them has no date, or for any other
 * requirement.
 */
fun Requirement.rowsCompletedDate(trackerEntries: Map<String, List<TrackerEntry>>): LocalDate? {
    val rowCount = tracker?.rowCount?.takeIf { completesFromRows } ?: return null
    return rowsCompletion(trackerEntries[number].orEmpty(), rowCount)?.date
}

/**
 * Whether enough of this requirement's children are complete that it needs no more of them,
 * even if its [own work][Requirement.ownWork] isn't done yet. Its other children are then not
 * needed.
 */
fun Requirement.hasEnoughChildren(
    progress: Map<String, RequirementProgress>,
    trackerEntries: Map<String, List<TrackerEntry>>,
    earnedBadges: EarnedBadges = EarnedBadges.None
): Boolean = children.isNotEmpty() &&
    childrenCompletion(progress, trackerEntries, earnedBadges) != null

/**
 * Whether any part of this requirement is done, complete or not: it or its
 * [own work][Requirement.ownWork] is marked complete, a row of its tracker is filled in, one of
 * the scout's [earnedBadges] counts toward the merit badges it asks for, or the same is true of a
 * requirement under it at any depth. A complete requirement under it has a part done too, so it
 * counts.
 */
fun Requirement.hasPartDone(
    progress: Map<String, RequirementProgress>,
    trackerEntries: Map<String, List<TrackerEntry>>,
    earnedBadges: EarnedBadges = EarnedBadges.None
): Boolean = ((isMarkedByHand || ownWork != null) && progress[number]?.completed == true) ||
    hasTrackerRows(trackerEntries[number].orEmpty()) ||
    (meritBadges?.let { earnedBadges.toward(it).counted > 0 } == true) ||
    children.any { it.hasPartDone(progress, trackerEntries, earnedBadges) }

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
    trackerEntries: Map<String, List<TrackerEntry>>,
    earnedBadges: EarnedBadges
): Completion? {
    val needed = neededCount
    val completed = children.mapNotNull { it.completion(progress, trackerEntries, earnedBadges) }
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
 * Whether a started badge or rank is complete on [version] of its requirements, or null if it
 * isn't. One marked completed on a prior date is complete on that date; otherwise it's complete
 * when all its top-level requirements are, on the last of their dates. A rank's requirements that
 * ask for merit badges count the scout's [earnedBadges].
 */
fun BadgeProgressDetails.completion(
    version: RequirementsVersion,
    earnedBadges: EarnedBadges = EarnedBadges.None
): Completion? {
    badge.completedOnPriorDate?.let { return Completion(it) }
    val progress = requirements.associateBy { it.requirementNumber }
    val entries = trackerEntries.groupBy { it.requirementNumber }
    return latestOf(
        version.requirements.map { it.completion(progress, entries, earnedBadges) ?: return null }
    )
}

/** Complete on the latest date of [completions], or with no date if one of them has none. */
private fun latestOf(completions: List<Completion>): Completion =
    Completion(completions.map { it.date ?: return Completion(null) }.maxOrNull())
