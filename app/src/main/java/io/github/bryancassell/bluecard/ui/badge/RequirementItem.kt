package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import io.github.bryancassell.bluecard.data.progress.TrackerEntry
import io.github.bryancassell.bluecard.data.progress.completion
import io.github.bryancassell.bluecard.data.progress.isMarkedByHand

/**
 * One requirement as a row: its number, our summary, whether it's complete, and how much of
 * its tracker is filled in. Every row opens the requirement's own page, for marking it
 * complete and for its sub-requirements, completion date, comment and tracker.
 */
data class RequirementItem(
    val number: String,
    val summary: String,
    /** How many of its sub-requirements are needed, or null when all of them are. */
    val choice: Choice?,
    val completed: Boolean,
    /**
     * Whether the scout marks it complete by hand, with a checkbox on its page. Otherwise its
     * sub-requirements or its tracker's rows decide ([isMarkedByHand]).
     */
    val markedByHand: Boolean,
    /** How much of its tracker is filled in, or null if it has none. */
    val tracker: TrackerCount? = null,
    /**
     * Whether it's no longer needed: it isn't complete, but a requirement it's part of is, such
     * as a choice the scout didn't pick once enough others are complete.
     */
    val notNeeded: Boolean = false
)

/** "Do [required] of [of]" sub-requirements. */
data class Choice(val required: Int, val of: Int)

/**
 * [progress] and [trackerEntries] are what the scout recorded on the badge, keyed by
 * requirement number. [partOfCompleted] is whether a requirement this one is part of, at any
 * depth, is complete.
 */
fun Requirement.toItem(
    progress: Map<String, RequirementProgress>,
    trackerEntries: Map<String, List<TrackerEntry>>,
    partOfCompleted: Boolean = false
): RequirementItem {
    val completed = completion(progress, trackerEntries) != null
    return RequirementItem(
        number = number,
        summary = summary,
        choice = choiceCount?.let { Choice(it, children.size) },
        completed = completed,
        markedByHand = isMarkedByHand,
        tracker = tracker?.count(trackerEntries[number].orEmpty()),
        notNeeded = partOfCompleted && !completed
    )
}
