package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.catalog.MeritBadge

/** How far the scout has got with a badge, declared from least to most progress. */
enum class BadgeStatus {
    NotStarted,
    InProgress,
    Completed
}

/**
 * The scout's status on this badge, from its recorded [progress] (null if it hasn't been
 * started). Completion is checked against the requirements version the badge is worked on.
 */
fun MeritBadge.status(progress: BadgeProgressDetails?): BadgeStatus {
    if (progress == null) return BadgeStatus.NotStarted
    // Checked first, because a badge marked completed on a prior date needs no version.
    if (progress.badge.completedOnPriorDate != null) return BadgeStatus.Completed
    // A badge on a version missing from the catalog can't be checked.
    val version = requirementsVersionFor(progress) ?: return BadgeStatus.InProgress
    val completed = progress.completion(version) != null
    return if (completed) BadgeStatus.Completed else BadgeStatus.InProgress
}
