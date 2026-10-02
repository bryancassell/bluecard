package io.github.bryancassell.bluecard.ui.badge

import androidx.lifecycle.SavedStateHandle
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.catalog.getAdvancements
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.badgeStart
import io.github.bryancassell.bluecard.ui.dateFromEpochDay
import java.time.Clock
import java.time.LocalDate

/**
 * Marks requirement [number] of badge or rank [advancementId] completed or not, for the page that
 * shows it, starting the badge or rank if it isn't ([badgeStart]).
 *
 * It remembers the date the requirement had when the scout unchecks it on the page: checking it
 * again there brings that date back, rather than today's, so a mistaken tap loses nothing. The
 * date is kept in the page's [savedStateHandle], not in the recorder, so it survives the system
 * stopping the app in the background, and is forgotten when the page closes. Its functions must be
 * called from the main thread, as [SavedStateHandle]'s are.
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
            val date = when (val unchecked = savedStateHandle.get<Any?>(UNCHECKED_DATE)) {
                // One that had no date, kept as null, or none unchecked on this page.
                null -> if (UNCHECKED_DATE in savedStateHandle) null else today

                // A value that isn't an epoch day is ignored.
                else -> dateFromEpochDay(unchecked) ?: today
            }
            progressRepository.markRequirementCompleted(
                advancementId,
                number,
                date,
                catalogRepository.getAdvancements().badgeStart(advancementId, today)
            )
            savedStateHandle.remove<Any?>(UNCHECKED_DATE)
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
     * Clears everything recorded for the requirements numbered in [numbers]. If this one is among
     * them, checking it again on this page then dates it today, even while the clear is being
     * saved: its date from before is forgotten first. A clear that fails forgets it too, as the
     * scout meant it to.
     */
    suspend fun clear(numbers: Collection<String>) {
        if (number in numbers) savedStateHandle.remove<Any?>(UNCHECKED_DATE)
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
