package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.progress.EarnedBadges
import io.github.bryancassell.bluecard.data.progress.MeritBadgeCredit
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import io.github.bryancassell.bluecard.data.progress.TrackerEntry
import io.github.bryancassell.bluecard.data.progress.completesFromRows
import io.github.bryancassell.bluecard.data.progress.completion
import io.github.bryancassell.bluecard.data.progress.hasEnoughChildren
import io.github.bryancassell.bluecard.data.progress.hasEnoughLogged
import io.github.bryancassell.bluecard.data.progress.hasEveryRow
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
     * requirements. "Not completed" beside "Completed on" read as a badge complete with nothing
     * done. One with parts recorded stays "Not completed", since "Not recorded" would contradict
     * the parts listed under it.
     */
    val notRecorded: Boolean = false,
    /**
     * The work it asks for besides its sub-requirements or its tracker's rows, or null if it asks
     * for none.
     */
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
     * Our summary of its [ownWork] while that's all that's left of it: it's still needed and
     * every sub-requirement it needs is complete, or every row of its tracker is filled in.
     * Otherwise null. It stays on a badge marked completed on a prior date, where it says what
     * was never recorded. With only its own work left, "(6 of 6 complete)" beside a box that
     * wasn't complete read as finished, and the scout had to open the page to find what was left
     * (#167). Counting the own work as one more part of [completeCount] instead read "Do 1 of 3
     * (1 of 2 complete)" on a choice, as if a second sub-requirement were needed (#254). The row
     * shows it whole, as a summary often ends with what's left, such as the counselor's
     * inspection.
     */
    val stillToDo: String? = null,
    /**
     * Whether its row says to check it off once all of it is done: it's still needed, and the
     * scout marks it complete by hand and has the number of rows or the amount it asks for in its
     * log ([hasEnoughLogged]). Those don't complete it, as it may ask for more, such as
     * Tenderfoot 6b's plan. It stays on a badge marked completed on a prior date, as [stillToDo]
     * does. Without it, "30 of 30 days" beside a box that wasn't complete read as finished
     * (#293). Unlike [stillToDo], the line doesn't name the rest of the requirement: the summary
     * above it says what it asks for, and many logs, such as Second Class 4's, ask for nothing
     * past the rows.
     */
    val checkOffLeft: Boolean = false,
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
 * Work a requirement asks for besides its sub-requirements or its tracker's rows
 * ([Requirement.ownWork]), which the scout marks complete by hand: our [summary] of it, and
 * whether it's [completed].
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
    val completeCount = if (stillNeeded) {
        completeCount(progress, trackerEntries, earnedBadges)
    } else {
        null
    }
    // Still needed with enough sub-requirements complete, or every row of its tracker filled in,
    // so its own work isn't.
    val onlyOwnWorkLeft = ownWork != null && stillNeeded &&
        (hasEnoughChildren(progress, trackerEntries, earnedBadges) || hasEveryRow(trackerEntries))
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
        completeCount = completeCount,
        stillToDo = ownWork?.takeIf { onlyOwnWorkLeft },
        checkOffLeft = stillNeeded && hasEnoughLogged(trackerEntries),
        completesFromRows = completesFromRows,
        meritBadges = meritBadges?.let(earnedBadges::toward)
    )
}

/**
 * How many of the children it needs are complete, or null if none are. It counts toward the number
 * needed, and never past it, so it says how close the requirement is. It's left out while none
 * are: "0 of 3" beside a tinted box, when only a requirement further down is complete, would read
 * as nothing done (#158).
 */
private fun Requirement.completeCount(
    progress: Map<String, RequirementProgress>,
    trackerEntries: Map<String, List<TrackerEntry>>,
    earnedBadges: EarnedBadges
): CompleteCount? {
    val complete = children.count { it.completion(progress, trackerEntries, earnedBadges) != null }
    // More than it needs are complete only while its own work isn't.
    return if (complete == 0) null else CompleteCount(minOf(complete, neededCount), neededCount)
}
