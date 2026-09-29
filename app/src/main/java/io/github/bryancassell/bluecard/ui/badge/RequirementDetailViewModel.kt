package io.github.bryancassell.bluecard.ui.badge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** One requirement of a badge and its sub-requirements, with the scout's progress. */
@HiltViewModel(assistedFactory = RequirementDetailViewModel.Factory::class)
class RequirementDetailViewModel @AssistedInject constructor(
    @Assisted("badgeId") badgeId: String,
    @Assisted("number") number: String,
    catalogRepository: CatalogRepository,
    progressRepository: ProgressRepository
) : ViewModel() {
    val uiState: StateFlow<RequirementDetailUiState> = combine(
        flow { emit(catalogRepository.getBadges().first { it.id == badgeId }) },
        progressRepository.observeProgress(badgeId)
    ) { badge, progress ->
        // This page opens from a row on the badge's page, which shows only requirements
        // that are in the catalog.
        val requirement = checkNotNull(badge.requirementsFor(progress)?.find(number)) {
            "Requirement $number of badge \"$badgeId\" isn't in the catalog"
        }
        val recorded = progress.requirementProgress()
        RequirementDetailUiState.Ready(
            badgeName = badge.name,
            requirement = requirement.toItem(recorded),
            children = requirement.children.map { it.toItem(recorded) }
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        RequirementDetailUiState.Loading
    )

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("badgeId") badgeId: String,
            @Assisted("number") number: String
        ): RequirementDetailViewModel
    }
}
