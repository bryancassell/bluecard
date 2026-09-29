package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.requirementsVersionFor
import java.time.LocalDate

// How the badge pages record progress. Recording anything starts the badge, so the scout
// never has to start it separately (ARCHITECTURE.md, Key flows).

/**
 * Starts [badge] on [today], if the scout hasn't started it, on the requirements version its
 * pages show until then: the newest ([requirementsVersionFor]).
 */
suspend fun ProgressRepository.startBadge(badge: MeritBadge, today: LocalDate) {
    // The pages show no requirements, so nothing to record, for a badge with no versions.
    val version = checkNotNull(badge.requirementsVersionFor(null)) { "${badge.id} has no versions" }
    startBadge(badge.id, version.effectiveDate, today)
}

/**
 * Marks requirement [number] of [badge] completed on [today], starting the badge first if
 * needed, or marks it not completed. The scout can change the date on the requirement's page.
 */
suspend fun ProgressRepository.setRequirementCompleted(
    badge: MeritBadge,
    number: String,
    completed: Boolean,
    today: LocalDate
) {
    if (completed) {
        startBadge(badge, today)
        markRequirementCompleted(badge.id, number, today)
    } else {
        markRequirementNotCompleted(badge.id, number)
    }
}
