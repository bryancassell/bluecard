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
 * started). Completion is checked against the requirements version the badge was started on.
 */
fun MeritBadge.status(progress: BadgeProgressDetails?): BadgeStatus {
    if (progress == null) return BadgeStatus.NotStarted
    // Checked first, because a badge marked completed on a prior date needs no version.
    if (progress.badge.completedOnPriorDate != null) return BadgeStatus.Completed
    val versionDate = progress.badge.requirementsVersion
    // Released catalogs keep every version they shipped, but a catalog edited during
    // development can drop one; a badge on a missing version can't be checked.
    val version = requirementVersions.find { it.effectiveDate == versionDate }
        ?: return BadgeStatus.InProgress
    val completed = progress.completion(version) != null
    return if (completed) BadgeStatus.Completed else BadgeStatus.InProgress
}
