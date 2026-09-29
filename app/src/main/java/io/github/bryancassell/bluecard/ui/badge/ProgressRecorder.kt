package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.progress.BadgeStart
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.requirementsVersionFor
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * Records progress on badge [badgeId] for one of its pages. Recording anything starts the
 * badge, in the same transaction, so the scout never has to start it separately
 * (ARCHITECTURE.md, Key flows).
 *
 * A page keeps one for as long as it's open, because it remembers the date of each
 * requirement the scout unchecks on it: checking the requirement again brings that date back,
 * rather than today's, so a mistaken tap loses nothing. The dates are forgotten when the page
 * closes. Its functions must be called one at a time, as a ViewModel's main thread does.
 */
class ProgressRecorder(
    private val badgeId: String,
    private val catalogRepository: CatalogRepository,
    private val progressRepository: ProgressRepository,
    private val clock: Clock
) {
    /** The dates of requirements unchecked on this page, by number; null for no date. */
    private val uncheckedDates = mutableMapOf<String, LocalDate?>()

    /** Marks requirement [number], one without sub-requirements, completed or not. */
    suspend fun setCompleted(number: String, completed: Boolean) {
        if (completed) {
            val date = if (number in uncheckedDates) uncheckedDates.getValue(number) else today()
            progressRepository.markRequirementCompleted(badgeId, number, date, badgeStart())
            uncheckedDates.remove(number)
        } else {
            val date = progressRepository.observeProgress(badgeId).first()
                ?.requirements?.find { it.requirementNumber == number }?.completedDate
            progressRepository.markRequirementNotCompleted(badgeId, number)
            uncheckedDates[number] = date
        }
    }

    /**
     * How the badge is started if the scout records something before starting it: on the
     * requirements version its pages show until then, the newest ([requirementsVersionFor]),
     * today.
     */
    suspend fun badgeStart(): BadgeStart {
        val badge = catalogRepository.getBadges().first { it.id == badgeId }
        // The pages show no requirements, so nothing to record, for a badge with no versions.
        val version =
            checkNotNull(badge.requirementsVersionFor(null)) { "$badgeId has no versions" }
        return BadgeStart(version.effectiveDate, today())
    }

    private fun today() = LocalDate.now(clock)
}
