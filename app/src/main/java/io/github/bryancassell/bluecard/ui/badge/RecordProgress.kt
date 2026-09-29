package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.progress.BadgeStart
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.requirementsVersionFor
import java.time.LocalDate

// How the badge pages record progress. Recording anything starts the badge, in the same
// transaction, so the scout never has to start it separately (ARCHITECTURE.md, Key flows).

/**
 * How the badge pages start this badge, if the scout hasn't started it, when [today] they
 * record something: on the requirements version the pages show until then, the newest
 * ([requirementsVersionFor]).
 */
fun MeritBadge.startedOn(today: LocalDate): BadgeStart {
    // The pages show no requirements, so nothing to record, for a badge with no versions.
    val version = checkNotNull(requirementsVersionFor(null)) { "$id has no versions" }
    return BadgeStart(version.effectiveDate, today)
}

/**
 * Marks requirement [number] of [badge] completed on [today], starting the badge if needed,
 * or marks it not completed. The scout can change the date on the requirement's page.
 */
suspend fun ProgressRepository.setRequirementCompleted(
    badge: MeritBadge,
    number: String,
    completed: Boolean,
    today: LocalDate
) {
    if (completed) {
        markRequirementCompleted(badge.id, number, today, badge.startedOn(today))
    } else {
        markRequirementNotCompleted(badge.id, number)
    }
}
