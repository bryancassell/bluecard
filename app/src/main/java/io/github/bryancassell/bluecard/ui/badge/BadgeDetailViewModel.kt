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
import io.github.bryancassell.bluecard.ui.launchSave
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** One badge from the catalog, with the scout's progress on its requirements. */
@HiltViewModel(assistedFactory = BadgeDetailViewModel.Factory::class)
class BadgeDetailViewModel @AssistedInject constructor(
    @Assisted private val badgeId: String,
    private val catalogRepository: CatalogRepository,
    private val progressRepository: ProgressRepository,
    private val clock: Clock
) : ViewModel() {
    private val saveFailed = MutableStateFlow(false)

    val uiState: StateFlow<BadgeDetailUiState> = combine(
        // What depends only on the catalog is worked out once, not on every progress change.
        flow {
            val catalog = catalogRepository.getBadges()
            emit(catalog to catalog.eagleGroups())
        },
        progressRepository.observeProgress(badgeId),
        saveFailed
    ) { (catalog, eagleGroups), progress, saveFailed ->
        val found = catalog.badgeRequirements(badgeId, progress)
            ?: return@combine BadgeDetailUiState.Unavailable
        val badge = found.badge
        BadgeDetailUiState.Ready(
            name = badge.name,
            summary = badge.summary,
            eagle = badge.eagleRequirement(eagleGroups),
            officialUrl = badge.officialUrl,
            requirements = found.version.requirements.map { it.toItem(found.recorded) },
            saveFailed = saveFailed
        )
    }.catchLoadFailure(BadgeDetailUiState.LoadFailed)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BadgeDetailUiState.Loading)

    /** Marks requirement [number], one without sub-requirements, completed today or not. */
    fun setCompleted(number: String, completed: Boolean) {
        viewModelScope.launchSave(saveFailed) {
            val badge = catalogRepository.getBadges().first { it.id == badgeId }
            progressRepository.setRequirementCompleted(
                badge,
                number,
                completed,
                LocalDate.now(clock)
            )
        }
    }

    /** The scout has been told that something couldn't be saved. */
    fun onSaveFailureShown() {
        saveFailed.value = false
    }

    @AssistedFactory
    interface Factory {
        fun create(badgeId: String): BadgeDetailViewModel
    }
}
