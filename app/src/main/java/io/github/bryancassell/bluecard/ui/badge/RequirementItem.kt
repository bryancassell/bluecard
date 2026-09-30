package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import io.github.bryancassell.bluecard.data.progress.TrackerEntry
import io.github.bryancassell.bluecard.data.progress.completion

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
     * Whether it has sub-requirements, which decide whether it's complete. One without them
     * is marked complete by the scout, on its own page.
     */
    val hasSubRequirements: Boolean,
    /** How much of its tracker is filled in, or null if it has none. */
    val tracker: TrackerCount? = null
)

/** "Do [required] of [of]" sub-requirements. */
data class Choice(val required: Int, val of: Int)

/**
 * [progress] and [trackerEntries] are what the scout recorded on the badge, keyed by
 * requirement number.
 */
fun Requirement.toItem(
    progress: Map<String, RequirementProgress>,
    trackerEntries: Map<String, List<TrackerEntry>>
) = RequirementItem(
    number = number,
    summary = summary,
    // A count of all the children is no choice.
    choice = requiredCount?.takeIf { it < children.size }?.let { Choice(it, children.size) },
    completed = completion(progress) != null,
    hasSubRequirements = children.isNotEmpty(),
    tracker = tracker?.count(trackerEntries[number].orEmpty())
)
