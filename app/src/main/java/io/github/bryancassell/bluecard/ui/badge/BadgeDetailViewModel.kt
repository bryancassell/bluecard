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
import io.github.bryancassell.bluecard.data.report.ReportRepository
import io.github.bryancassell.bluecard.ui.SaveFailure
import io.github.bryancassell.bluecard.ui.SaveRunner
import io.github.bryancassell.bluecard.ui.badges.eagleGroups
import io.github.bryancassell.bluecard.ui.badges.eagleRequirement
import io.github.bryancassell.bluecard.ui.catchLoadFailure
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * One badge from the catalog, with the scout's progress on its requirements, and its report
 * once it's complete.
 */
@HiltViewModel(assistedFactory = BadgeDetailViewModel.Factory::class)
class BadgeDetailViewModel @AssistedInject constructor(
    @Assisted private val badgeId: String,
    catalogRepository: CatalogRepository,
    progressRepository: ProgressRepository,
    private val reportRepository: ReportRepository
) : ViewModel() {
    // Reports are written like saves: a failure is logged and shown in a snackbar.
    private val reports = SaveRunner(viewModelScope)
    private val reportToShare = MutableStateFlow<Uri?>(null)

    /** The report being created to share, if there is one. */
    private var creatingReport: Job? = null

    val uiState: StateFlow<BadgeDetailUiState> = combine(
        // What depends only on the catalog is worked out once, not on every progress change.
        flow {
            val catalog = catalogRepository.getBadges()
            emit(catalog to catalog.eagleGroups())
        },
        progressRepository.observeProgress(badgeId),
        reportToShare,
        reports.failure
    ) { (catalog, eagleGroups), progress, reportToShare, reportFailure ->
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
            reportToShare = reportToShare,
            reportFailure = reportFailure
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
        creatingReport = reports.launch {
            reportToShare.value = reportRepository.createReportToShare(badgeId)
        }
    }

    /** The screen has opened the share sheet with the report. */
    fun onReportShared() {
        reportToShare.value = null
    }

    /** Saves the badge's report to [destination], a document the scout chose to create. */
    fun saveReport(destination: Uri) {
        reports.launch { reportRepository.saveReport(badgeId, destination) }
    }

    /** The scout has been told about [failure]. */
    fun onReportFailureShown(failure: SaveFailure) {
        reports.onShown(failure)
    }

    @AssistedFactory
    interface Factory {
        fun create(badgeId: String): BadgeDetailViewModel
    }
}
