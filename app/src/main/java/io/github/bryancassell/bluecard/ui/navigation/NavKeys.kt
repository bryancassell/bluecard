package io.github.bryancassell.bluecard.ui.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclassesOfSealed

/**
 * A Navigation 3 destination. Keys are @Serializable so the back stack survives configuration
 * changes and process death, and sealed so [BackStackSavedStateConfiguration] registers every one.
 */
@Serializable
sealed interface BlueCardNavKey : NavKey

/**
 * Saves and restores the back stack without reflection: each key's serializer is found from its
 * class in this module, and on restore from its serial name, which the compiler writes in as a
 * string, so R8 renaming the class doesn't change it.
 */
@OptIn(ExperimentalSerializationApi::class) // subclassesOfSealed, in kotlinx.serialization 1.11
val BackStackSavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) { subclassesOfSealed<BlueCardNavKey>() }
    }
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
