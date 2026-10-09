package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.progress.storedDate
import io.github.bryancassell.bluecard.ui.TaskFailure
import java.time.LocalDate

/** What the Tracker entry screen shows. */
sealed interface TrackerEntryUiState {
    /** The catalog or progress is still loading. */
    data object Loading : TrackerEntryUiState

    /** The catalog or progress couldn't be read. */
    data object LoadFailed : TrackerEntryUiState

    data class Ready(
        /** The name of the badge or rank the requirement is part of. */
        val advancementName: String,
        val requirementNumber: String,
        /** What one row is called, capitalized, for the title with [rowNumber]: "Week 3". */
        val rowTitle: String,
        /** The row it is, from 1: the one it fills, or its place in a log. */
        val rowNumber: Int,
        /** What one row is called, in lowercase, such as "session". */
        val rowLabel: String,
        /** A field for each, in order. Text and number columns edit the screen's text fields. */
        val columns: List<TrackerColumn>,
        /**
         * The date columns' dates, by column ID. A column without a date, or whose value isn't
         * one ([storedDate]), is left out.
         */
        val dates: Map<String, LocalDate>,
        /** Whether the fields have something in them that differs from what's saved. */
        val canSave: Boolean,
        /**
         * Whether the fields differ from what's saved, even if they're all empty, and aren't
         * being saved, so Back asks before discarding them.
         */
        val changed: Boolean,
        /** Whether the row has a saved entry, which Delete deletes. */
        val hasSavedEntry: Boolean,
        /** Whether Delete can be used now: not while a save is under way. */
        val canDelete: Boolean,
        /**
         * The names of the ranks, in the order they're earned, that would no longer count as
         * earned once the saved row is deleted, so the scout is told before deleting it. Deleting
         * a fixed-row tracker's row can leave its requirement incomplete: for a rank's
         * requirement, that un-earns its rank and those above it earned in order after it, and
         * for a badge's, those with merit badge requirements the badge completes, and those above
         * them. The row's page doesn't show them (#259).
         */
        val unearnedByDelete: List<String> = emptyList(),
        /** The entry was saved or deleted, so the screen closes. */
        val done: Boolean = false,
        /** Something couldn't be saved, and the scout hasn't been told yet. */
        val saveFailure: TaskFailure? = null
    ) : TrackerEntryUiState

    /**
     * The requirements the badge or rank uses don't have the tracker, or the row isn't in it, such
     * as when it was deleted.
     */
    data object Unavailable : TrackerEntryUiState
}
