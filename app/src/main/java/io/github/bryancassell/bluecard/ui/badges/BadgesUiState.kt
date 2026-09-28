package io.github.bryancassell.bluecard.ui.badges

/** What the Badges screen shows. */
sealed interface BadgesUiState {
    /** The catalog is still loading. */
    data object Loading : BadgesUiState

    /** Every badge in the catalog, in alphabetical order. */
    data class Ready(val badges: List<BadgeListItem>) : BadgesUiState
}

/** One badge in the list. */
data class BadgeListItem(
    val id: String,
    val name: String,
    val eagleRequired: Boolean,
    val status: BadgeStatus
)

/** How far the scout has got with a badge. */
enum class BadgeStatus {
    NotStarted,
    InProgress,
    Completed
}
