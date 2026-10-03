package io.github.bryancassell.bluecard.ui.rank

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
import io.github.bryancassell.bluecard.data.progress.RankStatus
import io.github.bryancassell.bluecard.data.progress.badgeStart
import io.github.bryancassell.bluecard.data.progress.earnedBadges
import io.github.bryancassell.bluecard.data.progress.standings
import io.github.bryancassell.bluecard.data.report.ReportRepository
import io.github.bryancassell.bluecard.ui.TaskFailure
import io.github.bryancassell.bluecard.ui.TaskRunner
import io.github.bryancassell.bluecard.ui.badge.ReportTasks
import io.github.bryancassell.bluecard.ui.badge.advancementRequirementsAmong
import io.github.bryancassell.bluecard.ui.catchLoadFailure
import io.github.bryancassell.bluecard.ui.dateFromEpochDay
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * One rank from the catalog, with the scout's progress on its requirements and their standing on
 * it, which depends on the ranks below and above it too, and its report once it's earned. While
 * it isn't earned, or counts as earned only with a rank above it, the scout can mark it earned on
 * a prior date. Once it's started, its progress can be cleared.
 */
@HiltViewModel(assistedFactory = RankDetailViewModel.Factory::class)
class RankDetailViewModel @AssistedInject constructor(
    @Assisted private val rankId: String,
    private val catalogRepository: CatalogRepository,
    private val progressRepository: ProgressRepository,
    reportRepository: ReportRepository,
    private val clock: Clock,
    // Keeps the date to open the date picker at if the system stops the app in the background.
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val report = ReportTasks(viewModelScope, reportRepository, rankId)
    private val saves = TaskRunner(viewModelScope)

    /**
     * The page as worked out from the catalog and progress, before [uiState] adds the date just
     * unmarked and the report.
     */
    private val rank: Flow<RankDetailUiState> = combine(
        flow { emit(catalogRepository.getRanks() to catalogRepository.getBadges()) },
        // Every rank's, because the ranks below and above this one decide whether it's earned,
        // and every badge's, which its requirements that ask for merit badges count.
        progressRepository.observeAllProgress(),
        saves.failure
    ) { (ranks, badges), allProgress, saveFailure ->
        val progressById = allProgress.associateBy { it.badge.badgeId }
        val earnedBadges = badges.earnedBadges(progressById)
        val standings = ranks.standings(progressById, earnedBadges)
        val standing = standings.find { it.rank.id == rankId }
            ?: return@combine RankDetailUiState.Unavailable
        val found =
            ranks.advancementRequirementsAmong(rankId, progressById, earnedBadges, standings)
                ?: return@combine RankDetailUiState.Unavailable
        val progress = progressById[rankId]
        RankDetailUiState.Ready(
            name = found.advancement.name,
            summary = found.advancement.summary,
            officialUrl = found.advancement.officialUrl,
            requirements = found.version.requirements.map(found::item),
            status = standing.status,
            fractionDone = standing.fractionDone,
            earnedOnPriorDate = progress?.badge?.completedOnPriorDate,
            earnedWith = standing.earnedWith?.name,
            earnedOn = standing.earnedOn,
            waitingOn = standing.waitingOn?.name,
            canClear = progress != null,
            unearnedByClear = if (progress == null) {
                emptyList()
            } else {
                // The other ranks earned now that wouldn't be once this one is cleared.
                standings.zip(ranks.standings(progressById - rankId, earnedBadges))
                    .filter { (now, cleared) ->
                        now.rank.id != rankId &&
                            now.status == RankStatus.Earned &&
                            cleared.status != RankStatus.Earned
                    }
                    .map { (now) -> now.rank.name }
            },
            saveFailure = saveFailure
        )
    }

    val uiState: StateFlow<RankDetailUiState> = combine(
        rank,
        savedStateHandle.getStateFlow<Any?>(UNMARKED_DATE, null),
        report.reportToShare,
        report.failure
    ) { state, unmarked, reportToShare, reportFailure ->
        // Combined after, so remembering a date or sharing a report doesn't work the rank's
        // standing out again.
        if (state is RankDetailUiState.Ready) {
            state.copy(
                unmarkedDate = dateFromEpochDay(unmarked),
                reportToShare = reportToShare,
                reportFailure = reportFailure
            )
        } else {
            state
        }
    }.catchLoadFailure(RankDetailUiState.LoadFailed)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RankDetailUiState.Loading)

    /**
     * Creates the rank's report, for the screen to open the share sheet with
     * ([RankDetailUiState.Ready.reportToShare]). Does nothing while one is being created, so a
     * double tap shares it once.
     */
    fun shareReport() {
        report.share()
    }

    /** The screen has opened the share sheet with the report. */
    fun onReportShared() {
        report.onShared()
    }

    /** Saves the rank's report to [destination], a document the scout chose to create. */
    fun saveReport(destination: Uri) {
        report.save(destination)
    }

    /** The scout has been told about [failure]. */
    fun onReportFailureShown(failure: TaskFailure) {
        report.onFailureShown(failure)
    }

    /**
     * Clears everything recorded for the rank, so it isn't started anymore. The page then shows
     * the requirements of the newest version. A report still being created to share is dropped,
     * so the share sheet doesn't open with what was cleared.
     */
    fun clear() {
        // Forgotten even if the clear fails, as the scout meant it to be.
        savedStateHandle[UNMARKED_DATE] = null
        report.follow(saves.launch { progressRepository.clearBadge(rankId) })
    }

    /**
     * Marks the rank earned on [date] without recording its requirements, as for a rank earned
     * before the scout used the app, or changes the date it's marked with. Every rank below it
     * then counts as earned too. A rank that isn't started is started, as when anything is
     * recorded.
     */
    fun markEarned(date: LocalDate) {
        report.follow(
            saves.launch {
                progressRepository.setCompletedOnPriorDate(
                    rankId,
                    date,
                    catalogRepository.getRanks().badgeStart(rankId, today())
                )
                savedStateHandle[UNMARKED_DATE] = null
            }
        )
    }

    /**
     * Undoes [markEarned], and with it what the mark counted as earned below it. What's recorded
     * for the rank stays. The page remembers the date it showed
     * ([RankDetailUiState.Ready.unmarkedDate]).
     */
    fun unmarkEarned() {
        val shown = (uiState.value as? RankDetailUiState.Ready)?.earnedOnPriorDate
        if (shown != null) savedStateHandle[UNMARKED_DATE] = shown.toEpochDay()
        report.follow(saves.launch { progressRepository.removeCompletedOnPriorDate(rankId) })
    }

    /** The scout has been told that a save failed ([failure]). */
    fun onSaveFailureShown(failure: TaskFailure) {
        saves.onShown(failure)
    }

    /** The latest date the rank can be marked earned on, read from the clock each time. */
    fun today(): LocalDate = LocalDate.now(clock)

    @AssistedFactory
    interface Factory {
        fun create(rankId: String): RankDetailViewModel
    }

    private companion object {
        /**
         * The epoch day of the date the rank was marked earned on before the scout unmarked it on
         * this page, or null. The date picker opens at it, so a mistaken Unmark loses nothing.
         * Forgotten once the rank is marked again or cleared, or when the page closes. It's set
         * to null rather than removed, which would stop uiState following it.
         */
        const val UNMARKED_DATE = "unmarkedDate"
    }
}
