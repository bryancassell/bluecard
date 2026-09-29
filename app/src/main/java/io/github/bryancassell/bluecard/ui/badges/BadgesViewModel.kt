package io.github.bryancassell.bluecard.ui.badges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.status
import java.text.Collator
import java.util.Locale
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
            // Badge names are English whatever the device language, so they're sorted by
            // English rules: an accented letter sorts with its base letter, and case only
            // breaks ties.
            val byName = compareBy(Collator.getInstance(Locale.ENGLISH), MeritBadge::name)
            val badges = catalogRepository.getBadges().sortedWith(byName)
            val eagleGroups = badges
                .mapNotNull { badge -> badge.eagleGroup?.let { it to badge.name } }
                .groupBy({ it.first }, { it.second })
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BadgesUiState.Loading)
}

/** [eagleGroups] maps each Eagle group to the names of its badges. */
private fun MeritBadge.eagleRequirement(eagleGroups: Map<String, List<String>>): EagleRequirement? {
    if (!eagleRequired) return null
    // The catalog is written in stages; while a group has only this badge so far, it's
    // shown as required on its own.
    val group = eagleGroup?.let(eagleGroups::get)?.takeIf { it.size > 1 }
        ?: return EagleRequirement.Required
    return EagleRequirement.OneOf(group)
}
