package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion

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
