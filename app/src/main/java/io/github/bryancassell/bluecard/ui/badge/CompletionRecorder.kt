package io.github.bryancassell.bluecard.ui.badge

import androidx.lifecycle.SavedStateHandle
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.catalog.getAdvancements
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.badgeStart
import java.time.Clock
import java.time.LocalDate

/**
 * Marks requirement [number] of badge or rank [advancementId] completed or not, for the page that
 * shows it, starting the badge or rank if it isn't ([badgeStart]).
 *
 * The page keeps one for as long as it's open, because it remembers the date the requirement had
 * when the scout unchecks it: checking it again brings that date back, rather than today's, so a
 * mistaken tap loses nothing. The date is kept in the page's [savedStateHandle], so it survives
 * the system stopping the app in the background, and is forgotten when the page closes. Its
 * functions must be called from one thread, such as a ViewModel's main thread.
 */
class CompletionRecorder(
    private val advancementId: String,
    private val number: String,
    private val catalogRepository: CatalogRepository,
    private val progressRepository: ProgressRepository,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle
) {
    /**
     * Marks the requirement, one the scout marks by hand, or its own work, for one with own work,
     * completed or not.
     */
    suspend fun setCompleted(completed: Boolean) {
        if (completed) {
            val today = LocalDate.now(clock)
            val date = if (UNCHECKED_DATE in savedStateHandle) uncheckedDate() else today
            progressRepository.markRequirementCompleted(
                advancementId,
                number,
                date,
                catalogRepository.getAdvancements().badgeStart(advancementId, today)
            )
            savedStateHandle.remove<Long>(UNCHECKED_DATE)
        } else {
            val before = progressRepository.markRequirementNotCompleted(advancementId, number)
            // Only one that was completed has a date to bring back. One unchecked twice, as by
            // a quick double tap, keeps the date from the first time.
            if (before?.completed == true) {
                savedStateHandle[UNCHECKED_DATE] = before.completedDate?.toEpochDay()
            }
        }
    }

    /**
     * The date the requirement had before the scout unchecked it on this page, which may be null,
     * to bring back. Read it only while [savedStateHandle] has a value under [UNCHECKED_DATE]: a
     * null value there, which it keeps like any other, is a requirement that had no date.
     */
    private fun uncheckedDate(): LocalDate? =
        savedStateHandle.get<Long>(UNCHECKED_DATE)?.let(LocalDate::ofEpochDay)

    /**
     * Clears everything recorded for the requirements numbered in [numbers]. If this one is among
     * them, checking it again on this page then dates it today, even while the clear is being
     * saved: its date from before is forgotten first. A clear that fails forgets it too, as the
     * scout meant it to.
     */
    suspend fun clear(numbers: Collection<String>) {
        if (number in numbers) savedStateHandle.remove<Long>(UNCHECKED_DATE)
        progressRepository.clearRequirements(advancementId, numbers)
    }

    private companion object {
        /**
         * The epoch day of the date the requirement had before the scout unchecked it on this
         * page, until they check it again, or null for no date.
         */
        const val UNCHECKED_DATE = "uncheckedDate"
    }
}
