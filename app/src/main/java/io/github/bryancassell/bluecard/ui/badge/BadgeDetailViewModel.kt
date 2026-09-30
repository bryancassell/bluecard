package io.github.bryancassell.bluecard.ui.badge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.ui.badges.eagleGroups
import io.github.bryancassell.bluecard.ui.badges.eagleRequirement
import io.github.bryancassell.bluecard.ui.catchLoadFailure
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** One badge from the catalog, with the scout's progress on its requirements. */
@HiltViewModel(assistedFactory = BadgeDetailViewModel.Factory::class)
class BadgeDetailViewModel @AssistedInject constructor(
    @Assisted badgeId: String,
    catalogRepository: CatalogRepository,
    progressRepository: ProgressRepository
) : ViewModel() {
    val uiState: StateFlow<BadgeDetailUiState> = combine(
        // What depends only on the catalog is worked out once, not on every progress change.
        flow {
            val catalog = catalogRepository.getBadges()
            emit(catalog to catalog.eagleGroups())
        },
        progressRepository.observeProgress(badgeId)
    ) { (catalog, eagleGroups), progress ->
        val found = catalog.badgeRequirements(badgeId, progress)
            ?: return@combine BadgeDetailUiState.Unavailable
        val badge = found.badge
        BadgeDetailUiState.Ready(
            name = badge.name,
            summary = badge.summary,
            eagle = badge.eagleRequirement(eagleGroups),
            officialUrl = badge.officialUrl,
            requirements = found.version.requirements.map(found::item),
            counselor = progress?.badge?.counselor
        )
    }.catchLoadFailure(BadgeDetailUiState.LoadFailed)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BadgeDetailUiState.Loading)

    @AssistedFactory
    interface Factory {
        fun create(badgeId: String): BadgeDetailViewModel
    }
}
