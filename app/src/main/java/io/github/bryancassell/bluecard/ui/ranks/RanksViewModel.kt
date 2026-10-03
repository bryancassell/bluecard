package io.github.bryancassell.bluecard.ui.ranks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.earnedBadges
import io.github.bryancassell.bluecard.data.progress.standings
import io.github.bryancassell.bluecard.ui.catchLoadFailure
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** The ranks in the catalog, in the order they're earned, with the scout's standing on each. */
@HiltViewModel
class RanksViewModel @Inject constructor(
    catalogRepository: CatalogRepository,
    progressRepository: ProgressRepository
) : ViewModel() {
    val uiState: StateFlow<RanksUiState> = combine(
        flow { emit(catalogRepository.getRanks() to catalogRepository.getBadges()) },
        progressRepository.observeAllProgress()
    ) { (ranks, badges), progress ->
        val progressById = progress.associateBy { it.badge.badgeId }
        val standings = ranks.standings(progressById, badges.earnedBadges(progressById))
        RanksUiState.Ready(
            standings.map { RankListItem(it.rank.id, it.rank.name, it.status, it.fractionDone) }
        )
    }.catchLoadFailure(RanksUiState.LoadFailed)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RanksUiState.Loading)
}
