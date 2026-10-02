package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import kotlinx.serialization.Serializable

/**
 * A Navigation 3 destination. Keys are @Serializable so the back stack survives configuration
 * changes and process death, and sealed so the compiler writes a serializer that knows them all.
 */
@Serializable
sealed interface BlueCardNavKey : NavKey

/**
 * The back stack, starting on Home, remembered across configuration changes and process death.
 * It's saved with [BlueCardNavKey]'s serializer, so no reflection is used: each key is saved by
 * its serial name, which the compiler writes in as a string, so R8 renaming the class doesn't
 * change it.
 */
@Composable
fun rememberBackStack(): NavBackStack<BlueCardNavKey> =
    rememberSerializable(serializer = NavBackStackSerializer(BlueCardNavKey.serializer())) {
        NavBackStack(Home)
    }

@Serializable
data object Onboarding : BlueCardNavKey

@Serializable
data object Home : BlueCardNavKey

@Serializable
data object Badges : BlueCardNavKey

@Serializable
data class BadgeDetail(val badgeId: String) : BlueCardNavKey

@Serializable
data object DataManagement : BlueCardNavKey

/** A requirement of a badge or rank, by its official number, such as "4c". */
@Serializable
data class RequirementDetail(val advancementId: String, val number: String) : BlueCardNavKey

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
) : BlueCardNavKey

/** The page for entering a badge's merit badge counselor. */
@Serializable
data class EditCounselor(val badgeId: String) : BlueCardNavKey

/** The page for changing the scout's name and unit number. */
@Serializable
data object EditProfile : BlueCardNavKey
