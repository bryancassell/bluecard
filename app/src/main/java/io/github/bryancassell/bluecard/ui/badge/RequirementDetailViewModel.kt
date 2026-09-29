package io.github.bryancassell.bluecard.ui.badge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.ui.catchLoadFailure
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
        flow { emit(catalogRepository.getBadges()) },
        progressRepository.observeProgress(badgeId)
    ) { catalog, progress ->
        // Checked on every change, not only when the page opens, because which version
        // the badge uses depends on its progress.
        val found = catalog.badgeRequirements(badgeId, progress)
        val requirement = found?.version?.find(number)
            ?: return@combine RequirementDetailUiState.Unavailable
        RequirementDetailUiState.Ready(
            badgeName = found.badge.name,
            requirement = requirement.toItem(found.recorded),
            children = requirement.children.map { it.toItem(found.recorded) }
        )
    }.catchLoadFailure(RequirementDetailUiState.LoadFailed).stateIn(
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
