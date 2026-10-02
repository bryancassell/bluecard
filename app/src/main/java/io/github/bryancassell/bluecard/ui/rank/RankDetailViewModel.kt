package io.github.bryancassell.bluecard.ui.rank

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
import io.github.bryancassell.bluecard.data.progress.standings
import io.github.bryancassell.bluecard.ui.TaskFailure
import io.github.bryancassell.bluecard.ui.TaskRunner
import io.github.bryancassell.bluecard.ui.badge.advancementRequirementsAmong
import io.github.bryancassell.bluecard.ui.catchLoadFailure
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * One rank from the catalog, with the scout's progress on its requirements and their standing on
 * it, which depends on the ranks below and above it too. While it isn't earned, or counts as
 * earned only with a rank above it, the scout can mark it earned on a prior date. Once it's
 * started, its progress can be cleared.
 */
@HiltViewModel(assistedFactory = RankDetailViewModel.Factory::class)
class RankDetailViewModel @AssistedInject constructor(
    @Assisted private val rankId: String,
    private val catalogRepository: CatalogRepository,
    private val progressRepository: ProgressRepository,
    private val clock: Clock
) : ViewModel() {
    private val saves = TaskRunner(viewModelScope)

    /**
     * The date the rank was marked earned on before the scout unmarked it on this page, or null.
     * The date picker opens at it, so a mistaken Unmark loses nothing. Forgotten once the rank is
     * marked again or cleared, or when the page closes.
     */
    private val unmarkedDate = MutableStateFlow<LocalDate?>(null)

    val uiState: StateFlow<RankDetailUiState> = combine(
        flow { emit(catalogRepository.getRanks()) },
        // Every rank's, because the ranks below and above this one decide whether it's earned.
        progressRepository.observeAllProgress(),
        saves.failure
    ) { ranks, allProgress, saveFailure ->
        val progressById = allProgress.associateBy { it.badge.badgeId }
        val standings = ranks.standings(progressById)
        val standing = standings.find { it.rank.id == rankId }
            ?: return@combine RankDetailUiState.Unavailable
        val found = ranks.advancementRequirementsAmong(rankId, progressById, standings)
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
                standings.zip(ranks.standings(progressById - rankId))
                    .filter { (now, cleared) ->
                        now.rank.id != rankId &&
                            now.status == RankStatus.Earned &&
                            cleared.status != RankStatus.Earned
                    }
                    .map { (now) -> now.rank.name }
            },
            saveFailure = saveFailure
        )
    }.combine(unmarkedDate) { state, unmarkedDate ->
        // Combined after, so remembering a date doesn't work the rank's standing out again.
        if (state is RankDetailUiState.Ready) state.copy(unmarkedDate = unmarkedDate) else state
    }.catchLoadFailure(RankDetailUiState.LoadFailed)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RankDetailUiState.Loading)

    /**
     * Clears everything recorded for the rank, so it isn't started anymore. The page then shows
     * the requirements of the newest version.
     */
    fun clear() {
        // Forgotten even if the clear fails, as the scout meant it to be.
        unmarkedDate.value = null
        saves.launch { progressRepository.clearBadge(rankId) }
    }

    /**
     * Marks the rank earned on [date] without recording its requirements, as for a rank earned
     * before the scout used the app, or changes the date it's marked with. Every rank below it
     * then counts as earned too. A rank that isn't started is started, as when anything is
     * recorded.
     */
    fun markEarned(date: LocalDate) {
        saves.launch {
            progressRepository.setCompletedOnPriorDate(
                rankId,
                date,
                catalogRepository.getRanks().badgeStart(rankId, today())
            )
            unmarkedDate.value = null
        }
    }

    /**
     * Undoes [markEarned], and with it what the mark counted as earned below it. What's recorded
     * for the rank stays. The page remembers the date it showed
     * ([RankDetailUiState.Ready.unmarkedDate]).
     */
    fun unmarkEarned() {
        val shown = (uiState.value as? RankDetailUiState.Ready)?.earnedOnPriorDate
        if (shown != null) unmarkedDate.value = shown
        saves.launch { progressRepository.removeCompletedOnPriorDate(rankId) }
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
}
