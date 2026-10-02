package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.catalog.Advancement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import java.time.LocalDate

/**
 * The requirements version this badge or rank is worked on, from the scout's [progress] on it
 * (null if it hasn't been started): the version it was started on, or the newest version for
 * one not started yet.
 *
 * Null if the catalog doesn't have that version, or has no versions for it. Released catalogs
 * keep every version they shipped and are validated in CI, so only a catalog edited during
 * development can cause that.
 */
fun Advancement.requirementsVersionFor(progress: BadgeProgressDetails?): RequirementsVersion? {
    if (progress == null) return requirementVersions.maxByOrNull { it.effectiveDate }
    return requirementVersions.find { it.effectiveDate == progress.badge.requirementsVersion }
}

/**
 * How badge or rank [badgeId] in this catalog is started if the scout records something before
 * starting it: on the requirements version its pages show until then ([requirementsVersionFor]),
 * dated [today]. Recording anything starts it, in the same transaction, so the scout never has
 * to start it separately (ARCHITECTURE.md, Recording progress).
 */
fun List<Advancement>.badgeStart(badgeId: String, today: LocalDate): BadgeStart {
    // The pages show nothing to record when the catalog doesn't have that version.
    val version = checkNotNull(find { it.id == badgeId }?.requirementsVersionFor(null)) {
        "The catalog has no requirements for $badgeId"
    }
    return BadgeStart(version.effectiveDate, today)
}
