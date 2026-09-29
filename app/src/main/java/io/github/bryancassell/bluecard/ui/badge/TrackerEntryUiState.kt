package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.ui.SaveFailure
import java.time.LocalDate

/** What the Tracker entry screen shows. */
sealed interface TrackerEntryUiState {
    /** The catalog or progress is still loading. */
    data object Loading : TrackerEntryUiState

    /** The catalog or progress couldn't be read. */
    data object LoadFailed : TrackerEntryUiState

    data class Ready(
        val badgeName: String,
        val requirementNumber: String,
        /** What one row is called, capitalized, for the title with [rowNumber]: "Week 3". */
        val rowTitle: String,
        /** The row it is, from 1: the one it fills, or its place in a log. */
        val rowNumber: Int,
        /** What one row is called, in lowercase, such as "session". */
        val rowLabel: String,
        /** A field for each, in order. Text and number columns edit the screen's text fields. */
        val columns: List<TrackerColumn>,
        /** The date columns' dates, by column ID. A column without a date is left out. */
        val dates: Map<String, LocalDate>,
        /** Whether the fields have something in them that differs from what's saved. */
        val canSave: Boolean,
        /** Whether there's a saved entry to delete. */
        val canDelete: Boolean,
        /** The latest date the scout can pick. */
        val today: LocalDate,
        /** The entry was saved or deleted, so the screen closes. */
        val done: Boolean = false,
        /** Something couldn't be saved, and the scout hasn't been told yet. */
        val saveFailure: SaveFailure? = null
    ) : TrackerEntryUiState

    /**
     * The requirements the badge uses don't have the tracker, or the row isn't in it, such as
     * when it was deleted.
     */
    data object Unavailable : TrackerEntryUiState
}
