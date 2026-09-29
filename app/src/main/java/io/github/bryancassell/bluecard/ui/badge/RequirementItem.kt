package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import io.github.bryancassell.bluecard.data.progress.completion

/**
 * One requirement as a row: its number, our summary and whether it's complete. Every row opens
 * the requirement's own page, for its sub-requirements, completion date and comment.
 */
data class RequirementItem(
    val number: String,
    val summary: String,
    /** How many of its sub-requirements are needed, or null when all of them are. */
    val choice: Choice?,
    val completed: Boolean,
    /**
     * Whether it has sub-requirements, which decide whether it's complete. One without them
     * is marked complete by the scout, with a checkbox on its row.
     */
    val hasSubRequirements: Boolean
)

/** "Do [required] of [of]" sub-requirements. */
data class Choice(val required: Int, val of: Int)

/** [progress] is the scout's recorded progress on the badge, keyed by requirement number. */
fun Requirement.toItem(progress: Map<String, RequirementProgress>) = RequirementItem(
    number = number,
    summary = summary,
    // A count of all the children is no choice.
    choice = requiredCount?.takeIf { it < children.size }?.let { Choice(it, children.size) },
    completed = completion(progress) != null,
    hasSubRequirements = children.isNotEmpty()
)
