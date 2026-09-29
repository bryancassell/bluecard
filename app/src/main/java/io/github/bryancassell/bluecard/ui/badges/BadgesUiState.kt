package io.github.bryancassell.bluecard.ui.badges

import io.github.bryancassell.bluecard.data.progress.BadgeStatus

/** What the Badges screen shows. */
sealed interface BadgesUiState {
    /** The catalog is still loading. */
    data object Loading : BadgesUiState

    /** The catalog or progress couldn't be read. */
    data object LoadFailed : BadgesUiState

    /**
     * The badges that match the search, in alphabetical order. Every badge in the catalog
     * when the search has no words, such as when it's blank or only punctuation.
     */
    data class Ready(val badges: List<BadgeListItem>) : BadgesUiState

    /** The scout searched for something, and no badge matches it. */
    data object NoMatches : BadgesUiState
}

/** One badge in the list. [eagle] is null for a badge that isn't Eagle-required. */
data class BadgeListItem(
    val id: String,
    val name: String,
    val eagle: EagleRequirement?,
    val status: BadgeStatus
)
