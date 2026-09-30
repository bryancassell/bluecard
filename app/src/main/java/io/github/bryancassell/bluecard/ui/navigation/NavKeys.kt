package io.github.bryancassell.bluecard.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Navigation 3 destinations. Keys are @Serializable so the back stack survives
// configuration changes and process death.

@Serializable
data object Onboarding : NavKey

@Serializable
data object Home : NavKey

@Serializable
data object Badges : NavKey

@Serializable
data class BadgeDetail(val badgeId: String) : NavKey

@Serializable
data object DataManagement : NavKey

/** A requirement of a badge, by its official number, such as "4c". */
@Serializable
data class RequirementDetail(val badgeId: String, val number: String) : NavKey

/**
 * A row of requirement [number]'s tracker: in a log, entry [entryId], or a new one when it's
 * null; in a tracker with a fixed number of rows, row [rowNumber]. The tracker decides which of
 * the two it goes by, so a row is opened with both.
 */
@Serializable
data class TrackerEntryDetail(
    val badgeId: String,
    val number: String,
    val entryId: Long? = null,
    val rowNumber: Int? = null
) : NavKey
