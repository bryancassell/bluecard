package io.github.bryancassell.bluecard.ui.data

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bluecard.data.backup.BackupReadResult
import io.github.bryancassell.bluecard.data.backup.BackupRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.ui.catchLoadFailure
import io.github.bryancassell.bluecard.ui.data.DataManagementMessage.Kind
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Exports the scout's data to a file, imports a file they exported, replacing everything once
 * they confirm it, and clears all their progress once they confirm that.
 */
@HiltViewModel
class DataManagementViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val progressRepository: ProgressRepository,
    private val clock: Clock
) : ViewModel() {
    /** Everything in [uiState] but [DataManagementUiState.canClear]. */
    private val _uiState = MutableStateFlow(DataManagementUiState())

    /**
     * Whether a badge is started. A StateFlow of its own, so the rest of [uiState] doesn't wait
     * for progress to be read again each time the screen comes back, such as from the file
     * picker. If progress can't be read, there's nothing known to clear. Home shows the
     * load-failed message then, so the scout reaches this screen that way only if the failure
     * comes after it opens.
     */
    private val canClear = progressRepository.observeAllProgress()
        .map { it.isNotEmpty() }
        .catchLoadFailure(false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val uiState: StateFlow<DataManagementUiState> =
        combine(_uiState, canClear) { uiState, canClear -> uiState.copy(canClear = canClear) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value)

    /** How many exports, reads, imports and clears are under way. */
    private var running = 0

    /** Today, read from the clock each time, for the name suggested for an export. */
    fun today(): LocalDate = LocalDate.now(clock)

    /** Exports the scout's data to [destination], a document they chose to create. */
    fun export(destination: Uri) = work(Kind.ExportFailed) {
        backupRepository.exportBackup(destination)
    }

    /**
     * Reads [source], a file the scout chose to import. If it can be imported, the screen asks
     * them to confirm ([DataManagementUiState.backupToImport]); otherwise it says why not.
     */
    fun read(source: Uri) = work(Kind.ReadFailed) {
        when (val result = backupRepository.readBackup(source)) {
            is BackupReadResult.Valid -> _uiState.update { it.copy(backupToImport = result.backup) }
            BackupReadResult.Invalid -> show(Kind.Invalid)
            BackupReadResult.NewerFormat -> show(Kind.NewerFormat)
        }
    }

    /** The scout confirmed replacing all their data with the file's. */
    fun confirmImport() {
        val backup = _uiState.value.backupToImport ?: return
        _uiState.update { it.copy(backupToImport = null) }
        work(Kind.ImportFailed) {
            backupRepository.importBackup(backup)
            show(Kind.Imported)
        }
    }

    /** The scout chose not to import the file. */
    fun cancelImport() {
        _uiState.update { it.copy(backupToImport = null) }
    }

    /** The scout confirmed clearing all their progress. Their profile stays. */
    fun clearAll() = work(Kind.ClearFailed) {
        progressRepository.clearAll()
        show(Kind.Cleared)
    }

    /** The screen has shown [shown]. A message since then stays, to be shown next. */
    fun onMessageShown(shown: DataManagementMessage) {
        _uiState.update { if (it.message === shown) it.copy(message = null) else it }
    }

    /**
     * Runs [action], marking the screen as working until it and any other action under way are
     * done, since one can start before another ends, as when two taps open two file pickers. If
     * it throws an [IOException], which repositories throw when a file or the scout's data can't
     * be read or saved, logs it and shows [failure]. Any other exception is a bug, so it still
     * crashes the app, as in TaskRunner.
     */
    private fun work(failure: Kind, action: suspend () -> Unit) {
        running++
        _uiState.update { it.copy(working = true) }
        viewModelScope.launch {
            try {
                action()
            } catch (e: IOException) {
                // The app reports caught exceptions nowhere else, so logcat and bug reports are
                // the only way to tell what failed and why.
                Log.w(TAG, "Export, import or clear failed: $failure", e)
                show(failure)
            } finally {
                running--
                _uiState.update { it.copy(working = running > 0) }
            }
        }
    }

    private fun show(kind: Kind) {
        _uiState.update { it.copy(message = DataManagementMessage(kind)) }
    }

    private companion object {
        const val TAG = "DataManagement"
    }
}
