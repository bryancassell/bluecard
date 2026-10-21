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
 * started), as [completion] decides.
 */
fun MeritBadge.status(progress: BadgeProgressDetails?): BadgeStatus = when {
    progress == null -> BadgeStatus.NotStarted
    completion(progress) != null -> BadgeStatus.Completed
    else -> BadgeStatus.InProgress
}

/**
 * Whether the scout completed this badge, from its recorded [progress] (null if it hasn't been
 * started), or null if they haven't. Completion is checked against the requirements version the
 * badge is worked on.
 */
fun MeritBadge.completion(progress: BadgeProgressDetails?): Completion? {
    if (progress == null) return null
    // Checked first, because a badge marked completed on a prior date needs no version.
    progress.badge.completedOnPriorDate?.let { return Completion(it) }
    // A badge on a version missing from the catalog can't be checked.
    val version = requirementsVersionFor(progress) ?: return null
    return progress.completion(version)
}
