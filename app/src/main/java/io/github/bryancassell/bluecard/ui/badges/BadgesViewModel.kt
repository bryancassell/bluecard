package io.github.bryancassell.bluecard.ui.badges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.status
import io.github.bryancassell.bluecard.ui.catchLoadFailure
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** Every badge in the catalog, with the scout's progress on each. */
@HiltViewModel
class BadgesViewModel @Inject constructor(
    catalogRepository: CatalogRepository,
    progressRepository: ProgressRepository
) : ViewModel() {
    val uiState: StateFlow<BadgesUiState> = combine(
        // What depends only on the catalog is worked out when the list starts collecting,
        // not on every progress change.
        flow {
            val catalog = catalogRepository.getBadges()
            val eagleGroups = catalog.eagleGroups()
            val badges = catalog.sortedWith(badgeNameOrder())
            emit(badges.map { it to it.eagleRequirement(eagleGroups) })
        },
        progressRepository.observeAllProgress()
    ) { badges, progress ->
        val progressById = progress.associateBy { it.badge.badgeId }
        BadgesUiState.Ready(
            badges.map { (badge, eagle) ->
                BadgeListItem(
                    id = badge.id,
                    name = badge.name,
                    eagle = eagle,
                    status = badge.status(progressById[badge.id])
                )
            }
        )
    }.catchLoadFailure(BadgesUiState.LoadFailed)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BadgesUiState.Loading)
}
