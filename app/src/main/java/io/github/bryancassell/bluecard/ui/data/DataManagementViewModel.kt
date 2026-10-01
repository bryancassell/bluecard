package io.github.bryancassell.bluecard.ui.data

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bluecard.data.backup.BackupReadResult
import io.github.bryancassell.bluecard.data.backup.BackupRepository
import io.github.bryancassell.bluecard.ui.data.DataManagementMessage.Kind
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Exports the scout's data to a file, and imports a file they exported, replacing everything
 * once they confirm it.
 */
@HiltViewModel
class DataManagementViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    clock: Clock
) : ViewModel() {
    private val _uiState = MutableStateFlow(DataManagementUiState(today = LocalDate.now(clock)))
    val uiState: StateFlow<DataManagementUiState> = _uiState.asStateFlow()

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

    /** The screen has shown [shown]. A message since then stays, to be shown next. */
    fun onMessageShown(shown: DataManagementMessage) {
        _uiState.update { if (it.message === shown) it.copy(message = null) else it }
    }

    /**
     * Runs [action], marking the screen as working until it's done. If it throws an
     * [IOException], which repositories throw when a file or the scout's data can't be read or
     * saved, logs it and shows [failure]. Any other exception is a bug, so it still crashes the
     * app, as in SaveRunner.
     */
    private fun work(failure: Kind, action: suspend () -> Unit) {
        _uiState.update { it.copy(working = true) }
        viewModelScope.launch {
            try {
                action()
            } catch (e: IOException) {
                // The app reports caught exceptions nowhere else, so logcat and bug reports are
                // the only way to tell what failed and why.
                Log.w(TAG, "Export or import failed: $failure", e)
                show(failure)
            } finally {
                _uiState.update { it.copy(working = false) }
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
