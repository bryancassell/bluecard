package io.github.bryancassell.bluecard.ui.data

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bluecard.data.backup.Backup
import io.github.bryancassell.bluecard.data.backup.BackupReadResult
import io.github.bryancassell.bluecard.data.backup.BackupRepository
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Exports the scout's data to a file, and imports a file they exported: merging it with their
 * data, asking which to keep where the two differ, or replacing everything with it. Clears all
 * their progress once they confirm it.
 */
@HiltViewModel
class DataManagementViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val catalogRepository: CatalogRepository,
    private val profileRepository: ProfileRepository,
    private val progressRepository: ProgressRepository,
    private val clock: Clock
) : ViewModel() {
    /** Everything in [uiState] but [DataManagementUiState.canClear]. */
    private val _uiState = MutableStateFlow(DataManagementUiState())

    /**
     * Whether a badge or rank is started. A StateFlow of its own, so the rest of [uiState]
     * doesn't wait for progress to be read again each time the screen comes back, such as from
     * the file picker. If progress can't be read, there's nothing known to clear. Home shows the
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
     * them whether to merge it or replace their data with it
     * ([DataManagementUiState.backupToImport]); otherwise it says why not.
     */
    fun read(source: Uri) = work(Kind.ReadFailed) {
        when (val result = backupRepository.readBackup(source)) {
            is BackupReadResult.Valid -> _uiState.update { it.copy(backupToImport = result.backup) }
            BackupReadResult.Invalid -> show(Kind.Invalid)
            BackupReadResult.NewerFormat -> show(Kind.NewerFormat)
        }
    }

    /** The scout chose to replace all their data with the file's. */
    fun replace() {
        val backup = _uiState.value.backupToImport ?: return
        _uiState.update { it.copy(backupToImport = null) }
        work(Kind.ImportFailed) {
            backupRepository.importBackup(backup)
            show(Kind.Imported)
        }
    }

    /**
     * The scout chose to merge the file with their data. Where the two differ, the screen asks
     * them which to keep ([DataManagementUiState.mergeChoices]); otherwise it merges straight
     * away.
     */
    fun merge() {
        val backup = _uiState.value.backupToImport ?: return
        _uiState.update { it.copy(backupToImport = null) }
        work(Kind.ImportFailed) {
            val profile = checkNotNull(profileRepository.observeProfile().first()) {
                "The scout hasn't saved their profile"
            }
            val phone = Backup(profile, progressRepository.observeAllProgress().first())
            val badges = catalogRepository.getBadges()
            val choices = mergeChoices(badges, catalogRepository.getRanks(), phone, backup)
            if (choices.isEmpty) {
                mergeWith(choices)
            } else {
                _uiState.update { it.copy(mergeChoices = choices) }
            }
        }
    }

    /** The scout chose the file's name and unit number to keep if [fromFile], or the phone's. */
    fun chooseProfile(fromFile: Boolean) = updateChoices { choices ->
        choices.copy(profile = choices.profile?.copy(fromFile = fromFile))
    }

    /**
     * The scout chose the file's progress on badge or rank [id] if [fromFile], or the phone's.
     * The ranks the merge would un-earn are worked out again.
     */
    fun chooseProgress(id: String, fromFile: Boolean) = updateChoices { choices ->
        choices.copy(
            advancements = choices.advancements.map {
                if (it.id == id) it.copy(fromFile = fromFile) else it
            }
        ).withUnearnedRanks()
    }

    /** The scout confirmed merging the file with their data, as they chose. */
    fun confirmMerge() {
        val choices = _uiState.value.mergeChoices ?: return
        _uiState.update { it.copy(mergeChoices = null) }
        work(Kind.ImportFailed) { mergeWith(choices) }
    }

    /** The scout chose not to import the file, before or after choosing to merge it. */
    fun cancelImport() {
        _uiState.update { it.copy(backupToImport = null, mergeChoices = null) }
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

    /**
     * Merges only the progress decided on from the data read when the scout chose to merge
     * ([MergeChoices.progressToMerge]), so a badge started or cleared on the phone since then
     * isn't replaced or added without being asked about.
     */
    private suspend fun mergeWith(choices: MergeChoices) {
        val file = Backup(choices.sources.file.profile, choices.progressToMerge)
        val profileFromFile = choices.profile?.fromFile == true
        backupRepository.mergeBackup(file, choices.fromFile, profileFromFile)
        show(Kind.Merged)
    }

    private fun updateChoices(change: (MergeChoices) -> MergeChoices) {
        _uiState.update { state -> state.copy(mergeChoices = state.mergeChoices?.let(change)) }
    }

    private fun show(kind: Kind) {
        _uiState.update { it.copy(message = DataManagementMessage(kind)) }
    }

    private companion object {
        const val TAG = "DataManagement"
    }
}
