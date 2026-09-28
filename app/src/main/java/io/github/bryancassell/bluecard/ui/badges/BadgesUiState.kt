package io.github.bryancassell.bluecard.ui.badges

/** What the Badges screen shows. */
sealed interface BadgesUiState {
    /** The catalog is still loading. */
    data object Loading : BadgesUiState

    /** Every badge in the catalog, in alphabetical order. */
    data class Ready(val badges: List<BadgeListItem>) : BadgesUiState
}

/** One badge in the list. [eagle] is null for a badge that isn't Eagle-required. */
data class BadgeListItem(
    val id: String,
    val name: String,
    val eagle: EagleRequirement?,
    val status: BadgeStatus
)

/** How a badge counts toward Eagle Scout. */
sealed interface EagleRequirement {
    /** Required on its own. */
    data object Required : EagleRequirement

    /**
     * One of a group of alternatives, such as Cycling, Hiking and Swimming. [badgeNames]
     * are the group's badges, this one included, in list order.
     */
    data class OneOf(val badgeNames: List<String>) : EagleRequirement
}

/** How far the scout has got with a badge. */
enum class BadgeStatus {
    NotStarted,
    InProgress,
    Completed
}
