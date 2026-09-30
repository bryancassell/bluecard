package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.progress.BadgeStart
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import java.time.Clock
import java.time.LocalDate

/**
 * Records progress on badge [badgeId] for one of its pages. Recording anything starts the
 * badge, in the same transaction, so the scout never has to start it separately
 * (ARCHITECTURE.md, Key flows).
 *
 * A page keeps one for as long as it's open, because it remembers the date of each
 * requirement the scout unchecks on it: checking the requirement again brings that date back,
 * rather than today's, so a mistaken tap loses nothing. The dates are forgotten when the page
 * closes. Its functions must be called from one thread, such as a ViewModel's main thread.
 */
class ProgressRecorder(
    private val badgeId: String,
    private val catalogRepository: CatalogRepository,
    private val progressRepository: ProgressRepository,
    private val clock: Clock
) {
    /** The dates of requirements unchecked on this page, by number; null for no date. */
    private val uncheckedDates = mutableMapOf<String, LocalDate?>()

    /** Marks requirement [number], one the scout marks by hand, completed or not. */
    suspend fun setCompleted(number: String, completed: Boolean) {
        if (completed) {
            val date = if (number in uncheckedDates) uncheckedDates.getValue(number) else today()
            progressRepository.markRequirementCompleted(badgeId, number, date, badgeStart())
            uncheckedDates.remove(number)
        } else {
            val before = progressRepository.markRequirementNotCompleted(badgeId, number)
            // Only one that was completed has a date to bring back. One unchecked twice, as by
            // a quick double tap, keeps the date from the first time.
            if (before?.completed == true) uncheckedDates[number] = before.completedDate
        }
    }

    /**
     * How the badge is started if the scout records something before starting it: on the
     * requirements version its pages show until then ([badgeRequirements]), today.
     */
    suspend fun badgeStart(): BadgeStart {
        // The pages show nothing to record when the catalog doesn't have that version.
        val shown = checkNotNull(catalogRepository.getBadges().badgeRequirements(badgeId, null)) {
            "The catalog has no requirements for $badgeId"
        }
        return BadgeStart(shown.version.effectiveDate, today())
    }

    private fun today() = LocalDate.now(clock)
}
