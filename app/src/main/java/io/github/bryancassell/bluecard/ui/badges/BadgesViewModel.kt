package io.github.bryancassell.bluecard.ui.badges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.completion
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
        flow {
            val badges = catalogRepository.getBadges()
            emit(badges.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, MeritBadge::name)))
        },
        progressRepository.observeAllProgress()
    ) { badges, progress ->
        val progressById = progress.associateBy { it.badge.badgeId }
        val eagleGroups = badges.filter { it.eagleGroup != null }
            .groupBy({ it.eagleGroup }, { it.name })
        BadgesUiState.Ready(
            badges.map { badge ->
                BadgeListItem(
                    id = badge.id,
                    name = badge.name,
                    eagleRequired = badge.eagleRequired,
                    status = badge.status(progressById[badge.id]),
                    // The catalog is written in stages; while a group has only this badge
                    // so far, it's shown as required on its own.
                    eagleGroup = eagleGroups[badge.eagleGroup]?.takeIf { it.size > 1 }.orEmpty()
                )
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BadgesUiState.Loading)
}

/** Completion is checked against the requirements version the badge was started on. */
private fun MeritBadge.status(progress: BadgeProgressDetails?): BadgeStatus {
    if (progress == null) return BadgeStatus.NotStarted
    // Checked first, because a badge marked completed on a prior date needs no version.
    if (progress.badge.completedOnPriorDate != null) return BadgeStatus.Completed
    val versionDate = progress.badge.requirementsVersion
    // Released catalogs keep every version they shipped, but a catalog edited during
    // development can drop one; a badge on a missing version can't be checked.
    val version = requirementVersions.find { it.effectiveDate == versionDate }
        ?: return BadgeStatus.InProgress
    val completed = progress.completion(version) != null
    return if (completed) BadgeStatus.Completed else BadgeStatus.InProgress
}
