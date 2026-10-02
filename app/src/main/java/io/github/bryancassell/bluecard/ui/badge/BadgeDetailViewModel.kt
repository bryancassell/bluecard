package io.github.bryancassell.bluecard.ui.badge

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.completion
import io.github.bryancassell.bluecard.data.progress.fractionDoneWhileInProgress
import io.github.bryancassell.bluecard.data.report.ReportRepository
import io.github.bryancassell.bluecard.ui.SaveFailure
import io.github.bryancassell.bluecard.ui.SaveRunner
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
    catalogRepository: CatalogRepository,
    private val progressRepository: ProgressRepository,
    private val reportRepository: ReportRepository,
    private val clock: Clock
) : ViewModel() {
    // Reports are written like saves: a failure is logged and shown in a snackbar, with a
    // message of its own.
    private val reports = SaveRunner(viewModelScope)
    private val saves = SaveRunner(viewModelScope)
    private val recorder = ProgressRecorder(badgeId, catalogRepository, progressRepository, clock)
    private val reportToShare = MutableStateFlow<Uri?>(null)

    /** The report being created to share, if there is one. */
    private var creatingReport: Job? = null

    /**
     * The latest clear. A report asked for after it waits for it, so it doesn't read the badge
     * from before, as it could in the moment before the page redraws without its report buttons.
     */
    private var clearing: Job? = null

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
        val found = catalog.badgeRequirements(badgeId, progress)
            ?: return@combine BadgeDetailUiState.Unavailable
        val badge = found.badge
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
    }.catchLoadFailure(BadgeDetailUiState.LoadFailed)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BadgeDetailUiState.Loading)

    /**
     * Creates the badge's report, for the screen to open the share sheet with
     * ([BadgeDetailUiState.Ready.reportToShare]). Does nothing while one is being created, so a
     * double tap shares it once.
     */
    fun shareReport() {
        if (creatingReport?.isActive == true) return
        val clear = clearing
        creatingReport = reports.launch {
            clear?.join()
            reportToShare.value = reportRepository.createReportToShare(badgeId)
        }
    }

    /** The screen has opened the share sheet with the report. */
    fun onReportShared() {
        reportToShare.value = null
    }

    /** Saves the badge's report to [destination], a document the scout chose to create. */
    fun saveReport(destination: Uri) {
        val clear = clearing
        reports.launch {
            clear?.join()
            reportRepository.saveReport(badgeId, destination)
        }
    }

    /** The scout has been told about [failure]. */
    fun onReportFailureShown(failure: SaveFailure) {
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
        clearing = saves.launch { progressRepository.clearBadge(badgeId) }
    }

    /**
     * Marks the badge completed on [date] without recording its requirements, as for a badge
     * earned before the scout used the app, or changes the date it's marked with. A badge that
     * isn't started is started, as when anything is recorded.
     */
    fun markCompleted(date: LocalDate) {
        saves.launch {
            progressRepository.setCompletedOnPriorDate(badgeId, date, recorder.badgeStart())
        }
    }

    /** Undoes [markCompleted]. What's recorded for the badge stays. */
    fun unmarkCompleted() {
        saves.launch { progressRepository.removeCompletedOnPriorDate(badgeId) }
    }

    /** The scout has been told that a save failed ([failure]). */
    fun onSaveFailureShown(failure: SaveFailure) {
        saves.onShown(failure)
    }

    /** The latest date the badge can be marked completed on, read from the clock each time. */
    fun today(): LocalDate = LocalDate.now(clock)

    @AssistedFactory
    interface Factory {
        fun create(badgeId: String): BadgeDetailViewModel
    }
}
