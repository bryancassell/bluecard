package io.github.bryancassell.bluecard.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.data.progress.BadgeStatus
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.status
import io.github.bryancassell.bluecard.ui.badges.BadgeListItem
import io.github.bryancassell.bluecard.ui.badges.EagleRequirement
import io.github.bryancassell.bluecard.ui.badges.badgeNameOrder
import io.github.bryancassell.bluecard.ui.badges.eagleGroups
import io.github.bryancassell.bluecard.ui.badges.eagleRequirement
import io.github.bryancassell.bluecard.ui.catchLoadFailure
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * The scout's profile, a summary of their progress and the badges they have in progress, kept
 * up to date as the profile or progress changes.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    profileRepository: ProfileRepository,
    catalogRepository: CatalogRepository,
    progressRepository: ProgressRepository
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(
        profileRepository.observeProfile(),
        // What depends only on the catalog is worked out when Home starts collecting, not on
        // every profile or progress change.
        flow {
            val catalog = catalogRepository.getBadges()
            val eagleGroups = catalog.eagleGroups()
            val badges = catalog.sortedWith(badgeNameOrder())
            emit(badges.map { it to it.eagleRequirement(eagleGroups) })
        },
        progressRepository.observeAllProgress()
    ) { profile, catalog, progress ->
        // Without a profile, the navigation root replaces Home with Onboarding.
        if (profile == null) return@combine HomeUiState.Loading
        val badges = catalog.map { (badge, _) -> badge }
        val progressById = progress.associateBy { it.badge.badgeId }
        val statusById = badges.associate { it.id to it.status(progressById[it.id]) }
        val eagle = eagleStatuses(badges, statusById)
        HomeUiState.Ready(
            name = profile.name,
            unitNumber = profile.unitNumber,
            badges = statusById.values.counts(),
            eagle = eagle.counts(),
            eagleTotal = eagle.size,
            badgesInProgress = badgesInProgress(catalog, statusById)
        )
    }.catchLoadFailure(HomeUiState.LoadFailed)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)
}

/**
 * One status for each Eagle-required badge, except that the badges in a "one of" group
 * share one. Earning any of them meets the requirement, so the group's status is that of
 * its furthest-along badge.
 */
private fun eagleStatuses(
    badges: List<MeritBadge>,
    statusById: Map<String, BadgeStatus>
): List<BadgeStatus> {
    val (grouped, single) = badges.filter { it.eagleRequired }.partition { it.eagleGroup != null }
    val groups = grouped.groupBy { it.eagleGroup }.values
    return single.map { statusById.getValue(it.id) } +
        groups.map { group -> group.maxOf { statusById.getValue(it.id) } }
}

/** The badges in progress, listed as on Badges, in [catalog]'s order. */
private fun badgesInProgress(
    catalog: List<Pair<MeritBadge, EagleRequirement?>>,
    statusById: Map<String, BadgeStatus>
): List<BadgeListItem> = catalog
    .filter { (badge, _) -> statusById.getValue(badge.id) == BadgeStatus.InProgress }
    .map { (badge, eagle) ->
        BadgeListItem(
            id = badge.id,
            name = badge.name,
            eagle = eagle,
            status = BadgeStatus.InProgress
        )
    }

private fun Collection<BadgeStatus>.counts() = ProgressCounts(
    completed = count { it == BadgeStatus.Completed },
    inProgress = count { it == BadgeStatus.InProgress }
)
