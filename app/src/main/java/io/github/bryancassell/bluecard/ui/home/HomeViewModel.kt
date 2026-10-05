package io.github.bryancassell.bluecard.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.catalog.eagleSlots
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.BadgeStatus
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.RankStatus
import io.github.bryancassell.bluecard.data.progress.earnedBadges
import io.github.bryancassell.bluecard.data.progress.standings
import io.github.bryancassell.bluecard.data.progress.status
import io.github.bryancassell.bluecard.ui.badges.BadgeListItem
import io.github.bryancassell.bluecard.ui.badges.ListedBadge
import io.github.bryancassell.bluecard.ui.badges.inListOrder
import io.github.bryancassell.bluecard.ui.catchLoadFailure
import io.github.bryancassell.bluecard.ui.ranks.toListItem
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * The scout's profile, their rank and the rank they're working toward, a summary of their badge
 * progress and the badges they have in progress, kept up to date as the profile or progress
 * changes.
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
            val badges = catalogRepository.getBadges()
            emit(Triple(badges, badges.inListOrder(), catalogRepository.getRanks()))
        },
        progressRepository.observeAllProgress()
    ) { profile, (badges, catalog, ranks), progress ->
        // Without a profile, the navigation root replaces Home with Onboarding.
        if (profile == null) return@combine HomeUiState.Loading
        val progressById = progress.associateBy { it.badge.badgeId }
        val standings = ranks.standings(progressById, badges.earnedBadges(progressById))
        val statusById = catalog.associate { (badge) ->
            badge.id to badge.status(progressById[badge.id])
        }
        val eagle = eagleStatuses(catalog, statusById)
        HomeUiState.Ready(
            name = profile.name,
            unitNumber = profile.unitNumber,
            rank = standings.lastOrNull { it.status == RankStatus.Earned }?.rank?.name,
            nextRank = standings.find { it.status == RankStatus.InProgress }?.toListItem(),
            badges = statusById.values.counts(),
            eagle = eagle.counts(),
            eagleTotal = eagle.size,
            badgesInProgress = badgesInProgress(catalog, statusById, progressById)
        )
    }.catchLoadFailure(HomeUiState.LoadFailed)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)
}

/**
 * One status for each Eagle-required badge, except that the badges in a "one of" group
 * share one ([eagleSlots]). Earning any of them meets the requirement, so the group's status is
 * that of its furthest-along badge.
 */
private fun eagleStatuses(
    catalog: List<ListedBadge>,
    statusById: Map<String, BadgeStatus>
): List<BadgeStatus> = catalog.map { it.badge }.eagleSlots()
    .map { slot -> slot.maxOf { statusById.getValue(it.id) } }

/** The badges in progress, listed as on Badges, in [catalog]'s order. */
private fun badgesInProgress(
    catalog: List<ListedBadge>,
    statusById: Map<String, BadgeStatus>,
    progressById: Map<String, BadgeProgressDetails>
): List<BadgeListItem> = catalog
    .filter { (badge) -> statusById.getValue(badge.id) == BadgeStatus.InProgress }
    .map { it.toListItem(progressById[it.badge.id]) }

private fun Collection<BadgeStatus>.counts() = ProgressCounts(
    completed = count { it == BadgeStatus.Completed },
    inProgress = count { it == BadgeStatus.InProgress }
)
