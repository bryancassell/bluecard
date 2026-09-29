package io.github.bryancassell.bluecard.ui.badges

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.status
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The badges in the catalog that match the scout's search, with their progress on each. */
@HiltViewModel
class BadgesViewModel @Inject constructor(
    catalogRepository: CatalogRepository,
    progressRepository: ProgressRepository,
    // Keeps the search if the system stops the app in the background.
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    /** The search field's text, which the field edits directly. */
    val query = TextFieldState(savedStateHandle[QUERY] ?: "")

    private val queryText = snapshotFlow { query.text.toString() }

    init {
        viewModelScope.launch { queryText.collect { savedStateHandle[QUERY] = it } }
    }

    val uiState: StateFlow<BadgesUiState> = combine(
        // What depends only on the catalog is worked out when the list starts collecting,
        // not on every progress change or keystroke.
        flow {
            val catalog = catalogRepository.getBadges()
            val eagleGroups = catalog.eagleGroups()
            val badges = catalog.sortedWith(badgeNameOrder())
            emit(badges.map { it to it.eagleRequirement(eagleGroups) })
        },
        progressRepository.observeAllProgress(),
        queryText
    ) { badges, progress, search ->
        val progressById = progress.associateBy { it.badge.badgeId }
        val matches = badges.filter { (badge, _) -> badge.matchesSearch(search) }
        if (matches.isEmpty()) {
            BadgesUiState.NoMatches
        } else {
            BadgesUiState.Ready(
                matches.map { (badge, eagle) ->
                    BadgeListItem(
                        id = badge.id,
                        name = badge.name,
                        eagle = eagle,
                        status = badge.status(progressById[badge.id])
                    )
                }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BadgesUiState.Loading)

    private companion object {
        const val QUERY = "query"
    }
}
