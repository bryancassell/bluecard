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

/** The page for entering a badge's merit badge counselor. */
@Serializable
data class EditCounselor(val badgeId: String) : NavKey
