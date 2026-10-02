package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.progress.BadgeStart
import java.time.Clock
import java.time.LocalDate

/**
 * How badge [badgeId] is started if the scout records something before starting it: on the
 * requirements version its pages show until then ([badgeRequirements]), today. Recording anything
 * starts the badge, in the same transaction, so the scout never has to start it separately
 * (ARCHITECTURE.md, Recording progress).
 */
suspend fun CatalogRepository.badgeStart(badgeId: String, clock: Clock): BadgeStart {
    // The pages show nothing to record when the catalog doesn't have that version.
    val shown = checkNotNull(getBadges().badgeRequirements(badgeId, null)) {
        "The catalog has no requirements for $badgeId"
    }
    return BadgeStart(shown.version.effectiveDate, LocalDate.now(clock))
}
