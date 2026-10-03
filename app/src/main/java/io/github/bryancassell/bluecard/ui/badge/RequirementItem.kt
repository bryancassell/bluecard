package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.progress.EarnedBadges
import io.github.bryancassell.bluecard.data.progress.MeritBadgeCredit
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import io.github.bryancassell.bluecard.data.progress.TrackerEntry
import io.github.bryancassell.bluecard.data.progress.completesFromRows
import io.github.bryancassell.bluecard.data.progress.completion
import io.github.bryancassell.bluecard.data.progress.hasEnoughChildren
import io.github.bryancassell.bluecard.data.progress.hasPartDone
import io.github.bryancassell.bluecard.data.progress.isMarkedByHand

/**
 * One requirement as a row: its number, our summary, whether it's complete or partly complete,
 * and how much of its tracker is filled in. Every row opens the requirement's own page, for
 * marking it or its own work complete and for its sub-requirements, completion date, comment and
 * tracker.
 */
data class RequirementItem(
    val number: String,
    val summary: String,
    /** How many of its sub-requirements are needed, or null when all of them are. */
    val choice: Choice?,
    val completed: Boolean,
    /**
     * Whether the scout marks it complete by hand, with a checkbox on its page. Otherwise its
     * sub-requirements, its tracker's rows or the scout's badges decide ([isMarkedByHand]); one
     * with [ownWork] has a checkbox for that work instead.
     */
    val markedByHand: Boolean,
    /** How much of its tracker is filled in, with its totals, or null if it has none. */
    val tracker: TrackerCount? = null,
    /**
     * Whether it's no longer needed: it isn't complete, but a requirement it's part of has enough
     * complete sub-requirements, such as a choice the scout didn't pick once enough others are
     * complete. That requirement may still need its own work.
     */
    val notNeeded: Boolean = false,
    /**
     * Whether nothing toward it was recorded: it's still needed and no part of it is done, but
     * the scout marked the badge or rank completed on a prior date, without recording its
     * requirements.
     */
    val notRecorded: Boolean = false,
    /** The work it asks for besides its sub-requirements, or null if it asks for none. */
    val ownWork: OwnWork? = null,
    /**
     * Whether part of it is done, though it isn't complete and is still needed: its own work, a
     * row of its tracker, or a requirement under it at any depth that's complete or partly done.
     */
    val partlyCompleted: Boolean = false,
    /**
     * How many of the sub-requirements it needs are complete, while it's [partlyCompleted] and
     * at least one is, or null.
     */
    val completeCount: CompleteCount? = null,
    /**
     * Whether it's complete once every row of its tracker is filled in ([completesFromRows]).
     * Its page then has the date it was completed on, which the scout can change.
     */
    val completesFromRows: Boolean = false,
    /**
     * For a rank's requirement that asks for merit badges, how far the scout's completed badges
     * go toward it, or null for any other requirement.
     */
    val meritBadges: MeritBadgeCredit? = null
)

/**
 * Work a requirement asks for besides its sub-requirements ([Requirement.ownWork]), which the
 * scout marks complete by hand: our [summary] of it, and whether it's [completed].
 */
data class OwnWork(val summary: String, val completed: Boolean)

/** "Do [required] of [of]" sub-requirements. */
data class Choice(val required: Int, val of: Int)

/** [complete] of the [needed] sub-requirements a requirement needs are complete. */
data class CompleteCount(val complete: Int, val needed: Int)

/**
 * [progress] and [trackerEntries] are what the scout recorded on the badge or rank, keyed by
 * requirement number. [partOfHasEnough] is whether a requirement this one is part of, at any
 * depth, has enough complete sub-requirements ([hasEnoughChildren]).
 * [advancementCompletedOnPriorDate] is whether the scout marked the badge or rank completed on a
 * prior date. [earnedBadges] are the badges the scout has completed, which a rank's requirement
 * that asks for merit badges counts.
 */
fun Requirement.toItem(
    progress: Map<String, RequirementProgress>,
    trackerEntries: Map<String, List<TrackerEntry>>,
    partOfHasEnough: Boolean = false,
    advancementCompletedOnPriorDate: Boolean = false,
    earnedBadges: EarnedBadges = EarnedBadges.None
): RequirementItem {
    val completed = completion(progress, trackerEntries, earnedBadges) != null
    val notNeeded = partOfHasEnough && !completed
    val stillNeeded = !completed && !notNeeded
    val partlyCompleted = stillNeeded && hasPartDone(progress, trackerEntries, earnedBadges)
    return RequirementItem(
        number = number,
        summary = summary,
        choice = choiceCount?.let { Choice(it, children.size) },
        completed = completed,
        markedByHand = isMarkedByHand,
        tracker = tracker?.count(trackerEntries[number].orEmpty()),
        notNeeded = notNeeded,
        notRecorded = advancementCompletedOnPriorDate && stillNeeded && !partlyCompleted,
        ownWork = ownWork?.let { OwnWork(it, progress[number]?.completed == true) },
        partlyCompleted = partlyCompleted,
        completeCount = if (stillNeeded) {
            completeCount(progress, trackerEntries, earnedBadges)
        } else {
            null
        },
        completesFromRows = completesFromRows,
        meritBadges = meritBadges?.let(earnedBadges::toward)
    )
}

/** How many of the children it needs are complete, or null if none are. */
private fun Requirement.completeCount(
    progress: Map<String, RequirementProgress>,
    trackerEntries: Map<String, List<TrackerEntry>>,
    earnedBadges: EarnedBadges
): CompleteCount? {
    val complete = children.count { it.completion(progress, trackerEntries, earnedBadges) != null }
    // More than it needs are complete only while its own work isn't.
    return if (complete == 0) null else CompleteCount(minOf(complete, neededCount), neededCount)
}
