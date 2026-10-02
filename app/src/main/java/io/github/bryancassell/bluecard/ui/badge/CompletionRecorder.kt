package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.catalog.getAdvancements
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import io.github.bryancassell.bluecard.data.progress.badgeStart
import java.time.Clock
import java.time.LocalDate

/**
 * Marks requirement [number] of badge or rank [advancementId] completed or not, for the page that
 * shows it, starting the badge or rank if it isn't ([badgeStart]).
 *
 * The page keeps one for as long as it's open, because it remembers the date the requirement had
 * when the scout unchecks it: checking it again brings that date back, rather than today's, so a
 * mistaken tap loses nothing. The date is forgotten when the page closes. Its functions must be
 * called from one thread, such as a ViewModel's main thread.
 */
class CompletionRecorder(
    private val advancementId: String,
    private val number: String,
    private val catalogRepository: CatalogRepository,
    private val progressRepository: ProgressRepository,
    private val clock: Clock
) {
    /**
     * The requirement as it was before the scout unchecked it on this page, until they check it
     * again; null if they haven't. Its date, which may be null, is the one to bring back.
     */
    private var unchecked: RequirementProgress? = null

    /**
     * Marks the requirement, one the scout marks by hand, or its own work, for one with own work,
     * completed or not.
     */
    suspend fun setCompleted(completed: Boolean) {
        if (completed) {
            val today = LocalDate.now(clock)
            val before = unchecked
            val date = if (before != null) before.completedDate else today
            progressRepository.markRequirementCompleted(
                advancementId,
                number,
                date,
                catalogRepository.getAdvancements().badgeStart(advancementId, today)
            )
            unchecked = null
        } else {
            val before = progressRepository.markRequirementNotCompleted(advancementId, number)
            // Only one that was completed has a date to bring back. One unchecked twice, as by
            // a quick double tap, keeps the date from the first time.
            if (before?.completed == true) unchecked = before
        }
    }

    /**
     * Clears everything recorded for the requirements numbered in [numbers]. If this one is among
     * them, checking it again on this page then dates it today, even while the clear is being
     * saved: its date from before is forgotten first. A clear that fails forgets it too, as the
     * scout meant it to.
     */
    suspend fun clear(numbers: Collection<String>) {
        if (number in numbers) unchecked = null
        progressRepository.clearRequirements(advancementId, numbers)
    }
}
