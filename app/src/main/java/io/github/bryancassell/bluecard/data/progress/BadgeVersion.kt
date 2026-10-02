package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import java.time.LocalDate

/**
 * The requirements version this badge is worked on, from the scout's [progress] on it (null
 * if it hasn't been started): the version it was started on, or the newest version for a
 * badge not started yet.
 *
 * Null if the catalog doesn't have that version, or has no versions for the badge. Released
 * catalogs keep every version they shipped and are validated in CI, so only a catalog edited
 * during development can cause that.
 */
fun MeritBadge.requirementsVersionFor(progress: BadgeProgressDetails?): RequirementsVersion? {
    if (progress == null) return requirementVersions.maxByOrNull { it.effectiveDate }
    return requirementVersions.find { it.effectiveDate == progress.badge.requirementsVersion }
}

/**
 * How badge [badgeId] is started if the scout records something before starting it: on the
 * requirements version its pages show until then ([requirementsVersionFor]), dated [today].
 * Recording anything starts the badge, in the same transaction, so the scout never has to start
 * it separately (ARCHITECTURE.md, Recording progress).
 */
suspend fun CatalogRepository.badgeStart(badgeId: String, today: LocalDate): BadgeStart {
    val badge = getBadges().find { it.id == badgeId }
    // The pages show nothing to record when the catalog doesn't have that version.
    val version = checkNotNull(badge?.requirementsVersionFor(null)) {
        "The catalog has no requirements for $badgeId"
    }
    return BadgeStart(version.effectiveDate, today)
}
