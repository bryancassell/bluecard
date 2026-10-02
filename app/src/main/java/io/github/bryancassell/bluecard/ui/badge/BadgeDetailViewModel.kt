package io.github.bryancassell.bluecard.ui.badge

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.badgeStart
import io.github.bryancassell.bluecard.data.progress.completion
import io.github.bryancassell.bluecard.data.progress.fractionDoneWhileInProgress
import io.github.bryancassell.bluecard.data.report.ReportRepository
import io.github.bryancassell.bluecard.ui.TaskFailure
import io.github.bryancassell.bluecard.ui.TaskRunner
import io.github.bryancassell.bluecard.ui.badges.eagleGroups
import io.github.bryancassell.bluecard.ui.badges.eagleRequirement
import io.github.bryancassell.bluecard.ui.catchLoadFailure
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * One badge from the catalog, with the scout's progress on its requirements, and its report
 * once it's complete. While it isn't complete, the scout can mark it completed on a prior date.
 * Once it's started, its progress can be cleared.
 */
@HiltViewModel(assistedFactory = BadgeDetailViewModel.Factory::class)
class BadgeDetailViewModel @AssistedInject constructor(
    @Assisted private val badgeId: String,
    private val catalogRepository: CatalogRepository,
    private val progressRepository: ProgressRepository,
    private val reportRepository: ReportRepository,
    private val clock: Clock,
    // Keeps the date to open Mark completed's picker at if the system stops the app in the
    // background.
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    // Separate runners, so a report that fails shows its own message, not a save's.
    private val reports = TaskRunner(viewModelScope)
    private val saves = TaskRunner(viewModelScope)
    private val reportToShare = MutableStateFlow<Uri?>(null)

    /** The report being created to share, if there is one. */
    private var creatingReport: Job? = null

    /**
     * The latest save. A report asked for after it waits for it, so it reads what the scout last
     * did: not the badge from before a clear, as it could in the moment before the page redraws
     * without its report buttons, or from before its date was changed.
     */
    private var lastSave: Job? = null

    val uiState: StateFlow<BadgeDetailUiState> = combine(
        // What depends only on the catalog is worked out once, not on every progress change.
        flow {
            val catalog = catalogRepository.getBadges()
            emit(catalog to catalog.eagleGroups())
        },
        progressRepository.observeProgress(badgeId),
        reportToShare,
        reports.failure,
        saves.failure
    ) { (catalog, eagleGroups), progress, reportToShare, reportFailure, saveFailure ->
        val found = catalog.advancementRequirements(badgeId, progress)
            ?: return@combine BadgeDetailUiState.Unavailable
        val badge = found.advancement
        BadgeDetailUiState.Ready(
            name = badge.name,
            summary = badge.summary,
            eagle = badge.eagleRequirement(eagleGroups),
            officialUrl = badge.officialUrl,
            requirements = found.version.requirements.map(found::item),
            counselor = progress?.badge?.counselor,
            completed = progress?.completion(found.version) != null,
            completedOnPriorDate = progress?.badge?.completedOnPriorDate,
            fractionDone = badge.fractionDoneWhileInProgress(progress),
            canClear = progress != null,
            reportToShare = reportToShare,
            reportFailure = reportFailure,
            saveFailure = saveFailure
        )
    }.combine(savedStateHandle.getStateFlow<Any?>(UNMARKED_DATE, null)) { state, unmarked ->
        // A value of another kind under the key is ignored, as in restoredText.
        val unmarkedDate = (unmarked as? Long)?.let(LocalDate::ofEpochDay)
        if (state is BadgeDetailUiState.Ready) state.copy(unmarkedDate = unmarkedDate) else state
    }.catchLoadFailure(BadgeDetailUiState.LoadFailed)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BadgeDetailUiState.Loading)

    /**
     * Creates the badge's report, for the screen to open the share sheet with
     * ([BadgeDetailUiState.Ready.reportToShare]). Does nothing while one is being created, so a
     * double tap shares it once.
     */
    fun shareReport() {
        if (creatingReport?.isActive == true) return
        val save = lastSave
        creatingReport = reports.launch {
            save?.join()
            reportToShare.value = reportRepository.createReportToShare(badgeId)
        }
    }

    /** The screen has opened the share sheet with the report. */
    fun onReportShared() {
        reportToShare.value = null
    }

    /** Saves the badge's report to [destination], a document the scout chose to create. */
    fun saveReport(destination: Uri) {
        val save = lastSave
        reports.launch {
            save?.join()
            reportRepository.saveReport(badgeId, destination)
        }
    }

    /** The scout has been told about [failure]. */
    fun onReportFailureShown(failure: TaskFailure) {
        reports.onShown(failure)
    }

    /**
     * Clears everything recorded for the badge, including its counselor, so it isn't started
     * anymore. The page then shows the requirements of the newest version. A report still being
     * created to share is dropped, so the share sheet doesn't open with what was cleared.
     */
    fun clear() {
        creatingReport?.cancel()
        reportToShare.value = null
        // Forgotten even if the clear fails, as the scout meant it to be.
        savedStateHandle[UNMARKED_DATE] = null
        lastSave = saves.launch { progressRepository.clearBadge(badgeId) }
    }

    /**
     * Marks the badge completed on [date] without recording its requirements, as for a badge
     * earned before the scout used the app, or changes the date it's marked with. A badge that
     * isn't started is started, as when anything is recorded.
     */
    fun markCompleted(date: LocalDate) {
        lastSave = saves.launch {
            progressRepository.setCompletedOnPriorDate(
                badgeId,
                date,
                catalogRepository.getBadges().badgeStart(badgeId, today())
            )
            savedStateHandle[UNMARKED_DATE] = null
        }
    }

    /**
     * Undoes [markCompleted]. What's recorded for the badge stays. The page remembers the date it
     * showed ([BadgeDetailUiState.Ready.unmarkedDate]).
     */
    fun unmarkCompleted() {
        val shown = (uiState.value as? BadgeDetailUiState.Ready)?.completedOnPriorDate
        if (shown != null) savedStateHandle[UNMARKED_DATE] = shown.toEpochDay()
        lastSave = saves.launch { progressRepository.removeCompletedOnPriorDate(badgeId) }
    }

    /** The scout has been told that a save failed ([failure]). */
    fun onSaveFailureShown(failure: TaskFailure) {
        saves.onShown(failure)
    }

    /** The latest date the badge can be marked completed on, read from the clock each time. */
    fun today(): LocalDate = LocalDate.now(clock)

    @AssistedFactory
    interface Factory {
        fun create(badgeId: String): BadgeDetailViewModel
    }

    private companion object {
        /**
         * The epoch day of the date the badge was marked completed on before the scout unmarked
         * it on this page, or null. Mark completed's picker opens at it, so a mistaken Unmark
         * loses nothing. Forgotten once the badge is marked again or cleared, or when the page
         * closes. It's set to null rather than removed, which would stop uiState following it.
         */
        const val UNMARKED_DATE = "unmarkedDate"
    }
}
