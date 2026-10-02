package io.github.bryancassell.bluecard.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Navigation 3 destinations. Keys are @Serializable so the back stack survives
// configuration changes and process death. rememberNavBackStack saves each key's class
// name and looks up its serializer with reflection, and restoring finds the class with
// Class.forName. In the R8-shrunk release build that relies on kotlinx.serialization's
// keep rules, so check a new key in a release build (docs/toolchain.md).

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

/** A requirement of a badge or rank, by its official number, such as "4c". */
@Serializable
data class RequirementDetail(val advancementId: String, val number: String) : NavKey

/**
 * A row of the tracker on requirement [number] of badge or rank [advancementId]: in a log, entry
 * [entryId], or a new one when it's null; in a tracker with a fixed number of rows, row
 * [rowNumber]. The tracker decides which of the two it goes by, so a row is opened with both.
 */
@Serializable
data class TrackerEntryDetail(
    val advancementId: String,
    val number: String,
    val entryId: Long? = null,
    val rowNumber: Int? = null
) : NavKey

/** The page for entering a badge's merit badge counselor. */
@Serializable
data class EditCounselor(val badgeId: String) : NavKey

/** The page for changing the scout's name and unit number. */
@Serializable
data object EditProfile : NavKey
