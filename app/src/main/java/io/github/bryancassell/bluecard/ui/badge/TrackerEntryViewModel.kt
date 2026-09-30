package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.TrackerEntry
import io.github.bryancassell.bluecard.data.progress.normalizedTrackerValues
import io.github.bryancassell.bluecard.ui.SaveFailure
import io.github.bryancassell.bluecard.ui.SaveRunner
import io.github.bryancassell.bluecard.ui.catchLoadFailure
import io.github.bryancassell.bluecard.ui.keepText
import io.github.bryancassell.bluecard.ui.restoredText
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * One row of a requirement's tracker, for the scout to fill in, change or delete: in a log,
 * entry [entryId], or a new one when it's null; in a tracker with a fixed number of rows, row
 * [rowNumber]. The tracker decides which of the two it goes by, so a row's page can be opened
 * with both.
 *
 * It reads what's saved when the page opens, and the page is a form: the scout saves it, which
 * closes the page, or leaves to discard their changes.
 */
@HiltViewModel(assistedFactory = TrackerEntryViewModel.Factory::class)
class TrackerEntryViewModel @AssistedInject constructor(
    @Assisted("badgeId") private val badgeId: String,
    @Assisted("number") private val number: String,
    @Assisted private val entryId: Long?,
    @Assisted private val rowNumber: Int?,
    catalogRepository: CatalogRepository,
    private val progressRepository: ProgressRepository,
    private val clock: Clock,
    // Keeps unsaved values if the system stops the app in the background.
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _fields = mutableMapOf<String, TextFieldState>()

    /**
     * The row's values as the scout edits them, by column ID: a text or number column's text,
     * and a date column's date as YYYY-MM-DD, or empty. They're filled in when the page first
     * loads, with the saved values or the unsaved ones the system stopped the app with.
     */
    val fields: Map<String, TextFieldState> = _fields

    private val saves = SaveRunner(viewModelScope)
    private val recorder = ProgressRecorder(badgeId, catalogRepository, progressRepository, clock)

    /** Whether a save or delete is under way. */
    private val saving = MutableStateFlow(false)

    /** Whether the row was saved or deleted, so the page closes. */
    private val done = MutableStateFlow(false)

    /**
     * The row, once the page has loaded it. It's loaded only once, so the page shown again after
     * a while, which restarts uiState, is the same form: still closing after a delete, say,
     * rather than finding the row gone.
     */
    private var loaded: LoadedRow? = null

    val uiState: StateFlow<TrackerEntryUiState> = flow {
        val row = loaded ?: load(
            catalogRepository.getBadges(),
            progressRepository.observeProgress(badgeId).first()
        )?.also { loaded = it }
        if (row == null) {
            emit(TrackerEntryUiState.Unavailable)
        } else {
            emitAll(
                combine(
                    snapshotFlow { _fields.mapValues { it.value.text.toString() } },
                    saving,
                    done,
                    saves.failure
                ) { values, saving, done, saveFailure ->
                    ready(row, values, saving, done, saveFailure)
                }
            )
        }
    }.catchLoadFailure(TrackerEntryUiState.LoadFailed).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TrackerEntryUiState.Loading
    )

    /** The row as the page loaded it: which one it is, and what it has saved. */
    private class LoadedRow(
        val badgeName: String,
        val tracker: TrackerDefinition,
        /** The row it is, from 1: the one it fills, or its place in a log. */
        val shownNumber: Int,
        /** Its saved entry; null for a new one or a row not filled in. */
        val entryId: Long?,
        val saved: Map<String, String>
    ) {
        /** The row it fills in a tracker with a fixed number of rows, or null in a log. */
        val rowNumber: Int? get() = tracker.rowCount?.let { shownNumber }
    }

    private fun ready(
        row: LoadedRow,
        values: Map<String, String>,
        saving: Boolean,
        done: Boolean,
        saveFailure: SaveFailure?
    ): TrackerEntryUiState.Ready {
        val columns = row.tracker.columns
        val stored = valuesToSave(row, values)
        return TrackerEntryUiState.Ready(
            badgeName = row.badgeName,
            requirementNumber = number,
            rowTitle = row.tracker.rowTitle,
            rowNumber = row.shownNumber,
            rowLabel = row.tracker.rowLabel,
            columns = columns,
            dates = columns.filter { it.type == TrackerColumnType.DATE }
                .mapNotNull { column ->
                    stored[column.id]?.let(::storedDate)?.let { column.id to it }
                }
                .toMap(),
            canSave = !saving && !done && isSavable(row, stored),
            hasSavedEntry = row.entryId != null,
            // Not while a save is under way, which would ignore it (finish).
            canDelete = !saving && !done && row.entryId != null,
            today = LocalDate.now(clock),
            done = done,
            saveFailure = saveFailure
        )
    }

    /**
     * Finds the row, and fills in the fields. Null if the requirements the badge uses don't have
     * the tracker, or the tracker doesn't have the row.
     */
    private fun load(catalog: List<MeritBadge>, progress: BadgeProgressDetails?): LoadedRow? {
        val found = catalog.badgeRequirements(badgeId, progress) ?: return null
        val tracker = found.version.find(number)?.tracker ?: return null
        val entries = found.trackerEntries[number].orEmpty()
        val rows = tracker.toItem(entries).rows
        val row = when {
            tracker.rowCount != null -> rows.find { it.number == rowNumber }
            entryId != null -> rows.find { it.entryId == entryId }
            else -> TrackerRow(rows.size + 1, null, emptyList()).also { closeIfAdded(entries) }
        } ?: return null
        val saved = entries.find { it.id == row.entryId }?.values.orEmpty()
        loadFields(tracker.columns, saved)
        return LoadedRow(found.badge.name, tracker, row.number, row.entryId, saved)
    }

    /**
     * Closes a new log entry's page if it has already added its entry, given the log's
     * [entries]. The system can stop the app after a save but before the page closes, as when
     * the scout leaves the app while saving; the page it restores then closes, rather than
     * offering to add the entry again. The page has added it if the log has an entry newer than
     * the page: IDs only grow.
     */
    private fun closeIfAdded(entries: List<TrackerEntry>) {
        // A value of another kind under the key is ignored, as in restoredText.
        val newest = savedStateHandle.get<Any?>(NEWEST_ENTRY) as? Long
            ?: (entries.maxOfOrNull { it.id } ?: 0L).also { savedStateHandle[NEWEST_ENTRY] = it }
        if (entries.any { it.id > newest }) done.value = true
    }

    /**
     * The row's values to save from the fields' [values], as stored. Values saved for columns
     * the tracker doesn't have are kept, so saving doesn't delete them. A shipped tracker never
     * loses a column (docs/catalog.md), but a catalog edited during development can. A number
     * without a digit, such as a lone ".", isn't one, so it's left out.
     */
    private fun valuesToSave(row: LoadedRow, values: Map<String, String>): Map<String, String> {
        val fields = row.tracker.columns.associate { column ->
            val text = values[column.id].orEmpty()
            val keep = column.type != TrackerColumnType.NUMBER || text.any { it.isDigit() }
            column.id to if (keep) text else ""
        }
        return normalizedTrackerValues(row.saved + fields)
    }

    /**
     * Whether [stored], the values to save ([valuesToSave]), can be saved as [row]: they differ
     * from what's saved, and the fields aren't all empty, since a row with nothing in its fields
     * is deleted instead.
     */
    private fun isSavable(row: LoadedRow, stored: Map<String, String>): Boolean =
        row.tracker.columns.any { it.id in stored } && stored != row.saved

    /**
     * Fills in the fields when the page loads: with the values the system stopped the app with,
     * or else the [saved] ones. They're kept from then on, so if the system stops the app before
     * then, the page loads the saved values again.
     */
    private fun loadFields(columns: List<TrackerColumn>, saved: Map<String, String>) {
        val restored = columns.associate { it.id to savedStateHandle.restoredText(fieldKey(it.id)) }
        // All are kept together, so if one wasn't restored, such as when another value is under
        // its key, the saved values are loaded into all of them, as in StoredTextFields. Leaving
        // it empty would erase its saved value on the next save.
        val wasStopped = restored.values.all { it != null }
        for (column in columns) {
            val text = if (wasStopped) restored[column.id] else saved[column.id]
            val field = TextFieldState(text.orEmpty())
            savedStateHandle.keepText(fieldKey(column.id), field)
            _fields[column.id] = field
        }
    }

    /** Sets date column [columnId] to [date], or removes its date (null). */
    fun setDate(columnId: String, date: LocalDate?) {
        // In a snapshot of its own, so uiState sees the change as soon as it's applied, not
        // when Compose next applies changes made outside a snapshot.
        Snapshot.withMutableSnapshot {
            _fields.getValue(columnId).setTextAndPlaceCursorAtEnd(date?.toString().orEmpty())
        }
    }

    /**
     * Saves the fields as the row, then closes the page. Does nothing if they can't be saved
     * ([TrackerEntryUiState.Ready.canSave]), which the Save button may not show yet.
     */
    fun save() {
        val row = loaded ?: return
        val values = valuesToSave(row, _fields.mapValues { it.value.text.toString() })
        if (!isSavable(row, values)) return
        finish {
            // An entry deleted since the page loaded is added again, keeping the scout's edit.
            // That happens if they delete it and reopen it before the delete is saved.
            progressRepository.addTrackerEntry(
                badgeId,
                number,
                row.rowNumber,
                values,
                recorder.badgeStart(),
                id = row.entryId
            )
        }
    }

    /** Deletes the saved row, then closes the page. */
    fun delete() {
        val id = loaded?.entryId ?: return
        finish { progressRepository.deleteTrackerEntry(id) }
    }

    /**
     * Runs [write], then closes the page if it worked. Does nothing while another is under way
     * or once the page is closing, so a double tap saves once.
     */
    private fun finish(write: suspend () -> Unit) {
        if (saving.value || done.value) return
        saving.value = true
        saves.launch {
            try {
                write()
                done.value = true
            } finally {
                saving.value = false
            }
        }
    }

    /** The scout has been told about [failure]. */
    fun onSaveFailureShown(failure: SaveFailure) {
        saves.onShown(failure)
    }

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("badgeId") badgeId: String,
            @Assisted("number") number: String,
            entryId: Long?,
            rowNumber: Int?
        ): TrackerEntryViewModel
    }

    private companion object {
        const val NEWEST_ENTRY = "newestEntry"

        fun fieldKey(columnId: String) = "field:$columnId"
    }
}
