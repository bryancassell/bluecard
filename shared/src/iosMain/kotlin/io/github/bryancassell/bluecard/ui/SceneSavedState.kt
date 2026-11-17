package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.MutableDoubleState
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.snapshots.SnapshotMutableState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.toMutableStateList
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.savedState
import io.github.bryancassell.bluecard.di.WarningLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import platform.Foundation.NSUserDefaults

/**
 * What the app's pages remember, as text the iOS app gives iOS to keep with its scene
 * (`@SceneStorage` in ContentView.swift). So the back stack and what the scout typed come back
 * after iOS ends the app in the background, as Android's saved state does. Unlike Android, iOS
 * keeps it when the scout closes the app in the app switcher too (PRD.md, Returning to the app).
 *
 * Compose Multiplatform 1.12.2 keeps saved state only in memory on iOS: ComposeContainer.ios.kt
 * keeps it in `private var savedState`, for its next scene in the same process, and
 * ComposeUIViewController takes none. So the app's content gets [registry] as its
 * LocalSaveableStateRegistry, in place of the container's. Every page's state goes through it:
 * the back stack is saved with rememberSerializable, and Navigation 3's
 * SaveableStateHolderNavEntryDecorator gives each page a registry that saves into this one, which
 * the page's ViewModels keep their SavedStateHandles in (navigation3-runtime 1.2.0,
 * lifecycle-viewmodel-navigation3 2.11.0). A ViewModel outside the pages, such as
 * MainActivityViewModel, would keep its SavedStateHandle in the container's registry, so only in
 * memory.
 *
 * [registry] accepts every value, but the text keeps only the types a SavedState holds, as
 * Android's keeps what a Bundle holds, and the maps, lists and states that SaveableStateHolder
 * and rememberSaveable save, each as its own type. An entry of a map or SavedState that holds
 * another value, such as an enum or a lazy list item's default key, is left out, with a warning,
 * so it starts fresh after iOS ends the app. Android keeps those, since they're Serializable or
 * Parcelable there. Refusing them, as Android refuses what a Bundle can't hold, would crash only
 * on iOS, the first time code that works on Android is composed. A SnapshotStateMap is left out
 * too, which Android refuses: it's neither Parcelable nor Serializable there (runtime-android
 * 1.12.1), unlike SnapshotStateList.
 *
 * iOS keeps a scene's state when the app is installed over it, even by the same build (checked
 * on an iOS 27.0 simulator), so the text names the [build] that saved it, and state saved by
 * another build is dropped. A page's state could otherwise be read by code that saves it
 * differently, such as a back stack naming a key that was renamed, which Android avoids by
 * dropping an app's saved state when it's updated. Builds with the same version and build number,
 * such as two debug builds, share it.
 *
 * iOS keeps it after the app crashes too, even at every launch (checked on an iOS 27.0
 * simulator), so a page that crashed as it was restored would crash every launch until the next
 * build. So [defaults] say while the pages are open: from when they're restored, and each time
 * they become active ([resumed]), until their state is saved as they go inactive or to the
 * background ([paused]). If they're still open at launch, the last run ended while the app was
 * open, as a crash does, and the pages start at Home, as UIKit's own state restoration does after
 * a crash at launch (Apple's sample "Restoring your app's state"). The flag set at launch outlived
 * a crash as the pages were restored, even for a relaunch a second later, though the preferences
 * file changed only seconds after: the system's preferences service had it, and the app reads it
 * from there (checked on an iOS 27.0 simulator). It's one flag for the app, which has one scene
 * (UIApplicationSupportsMultipleScenes in iosApp/Info.plist).
 */
internal class SceneSavedState(
    saved: String,
    private val build: String,
    private val defaults: NSUserDefaults,
    private val warningLog: WarningLog
) {
    val registry: SaveableStateRegistry

    init {
        val endedWhileOpen = defaults.boolForKey(OPEN)
        resumed()
        registry = SaveableStateRegistry(restore(saved, endedWhileOpen)) { true }
    }

    /** Called as the pages become active (ON_RESUME). */
    fun resumed() {
        defaults.setBool(true, OPEN)
    }

    /**
     * What [registry] holds now, as the text a new [SceneSavedState] restores it from, for the
     * app to keep as the pages go inactive (ON_PAUSE) or to the background (ON_STOP).
     */
    fun paused(): String {
        val writer = Writer()
        val values = writer.keeping(registry.performSave().entries) {
            it.key to JsonArray(it.value.map(writer::write))
        }
        if (writer.leftOut.isNotEmpty()) {
            warningLog.warn(
                TAG,
                "Left out ${writer.leftOut.size} saved values it can't write, which start " +
                    "fresh if iOS ends the app",
                writer.leftOut.first()
            )
        }
        defaults.setBool(false, OPEN)
        return JsonObject(
            mapOf(BUILD to JsonPrimitive(build), VALUES to JsonObject(values.toMap()))
        ).toString()
    }

    /**
     * The values [paused] wrote as [saved], or null if there are none, the last run ended while
     * the app was open, another build saved them, or they can't be read. The build is checked
     * before the values are read, so a later build can save them another way.
     */
    private fun restore(saved: String, endedWhileOpen: Boolean): Map<String, List<Any?>>? {
        if (saved.isEmpty()) return null
        if (endedWhileOpen) {
            warningLog.warn(TAG, "The last run ended while the app was open, so it starts at Home")
            return null
        }
        return try {
            val state = Json.parseToJsonElement(saved).jsonObject
            if (state[BUILD]?.jsonPrimitive?.content == build) {
                state.getValue(VALUES).jsonObject.mapValues { it.value.jsonArray.map(::decode) }
            } else {
                null
            }
        } catch (e: RuntimeException) {
            // Text this build can't read, as from a bug, which would crash every launch too.
            // Reading it throws more than one kind (kotlinx-serialization 1.12.0, checked by
            // SceneSavedStateTest): a SerializationException for text that isn't JSON, an
            // IllegalArgumentException or, for a Boolean, an IllegalStateException for an
            // element of the wrong kind, and a NoSuchElementException or IndexOutOfBoundsException
            // for a missing one.
            warningLog.warn(
                TAG,
                "Couldn't read the pages' saved state, so the app starts at Home",
                e
            )
            null
        }
    }

    private companion object {
        const val BUILD = "build"
        const val VALUES = "values"

        /** The [defaults] key that says the pages are open. */
        const val OPEN = "pagesOpen"
        const val TAG = "SceneSavedState"
    }
}

/** The app's version and build number, such as "0.1.0 (1)", from its Info.plist's [info]. */
internal fun appBuild(info: (key: String) -> Any?): String =
    "${info("CFBundleShortVersionString")} (${info("CFBundleVersion")})"

/**
 * Writes values as JSON that [decode] reads back as the same type, since a page reads each value
 * back as the type it saved, such as an Int and not a Long, or a MutableIntState and not a
 * MutableState. It leaves out an entry of a map or SavedState that holds a value it can't write,
 * and keeps why in [leftOut].
 */
private class Writer {
    val leftOut = mutableListOf<IllegalArgumentException>()

    /** [items], each written by [write], leaving out one that holds a value it can't write. */
    fun <E, T> keeping(items: Iterable<E>, write: (E) -> T): List<T> = items.mapNotNull {
        try {
            write(it)
        } catch (e: IllegalArgumentException) {
            leftOut += e
            null
        }
    }

    /** [value] as JSON, or an IllegalArgumentException for a value it can't write. */
    fun write(value: Any?): JsonElement = when (value) {
        null -> JsonNull

        is String -> typed("String", JsonPrimitive(value))

        is Boolean -> typed("Boolean", JsonPrimitive(value))

        is Char -> typed("Char", JsonPrimitive(value.toString()))

        is Int -> typed("Int", JsonPrimitive(value))

        is Long -> typed("Long", JsonPrimitive(value))

        // As text, since JSON has no NaN or infinity, and Kotlin reads its text of a number back
        // as the same number.
        is Float -> typed("Float", JsonPrimitive(value.toString()))

        is Double -> typed("Double", JsonPrimitive(value.toString()))

        is BooleanArray -> typed("BooleanArray", JsonArray(value.map { JsonPrimitive(it) }))

        is CharArray -> typed("CharArray", JsonPrimitive(value.concatToString()))

        is IntArray -> typed("IntArray", JsonArray(value.map { JsonPrimitive(it) }))

        is LongArray -> typed("LongArray", JsonArray(value.map { JsonPrimitive(it) }))

        is FloatArray ->
            typed("FloatArray", JsonArray(value.map { JsonPrimitive(it.toString()) }))

        is DoubleArray ->
            typed("DoubleArray", JsonArray(value.map { JsonPrimitive(it.toString()) }))

        is Array<*> -> typed("Array", JsonArray(value.map(::write)))

        // Before List, which it is too.
        is SnapshotStateList<*> -> typed("SnapshotStateList", JsonArray(value.map(::write)))

        is List<*> -> typed("List", JsonArray(value.map(::write)))

        is SnapshotStateMap<*, *> ->
            throw IllegalArgumentException("A SnapshotStateMap can't be saved, as on Android")

        is Map<*, *> ->
            typed(
                "Map",
                JsonArray(
                    keeping(value.entries) {
                        JsonArray(listOf(write(it.key), write(it.value)))
                    }
                )
            )

        is SavedState ->
            typed(
                "SavedState",
                JsonObject(
                    keeping(
                        value.read {
                            toMap()
                        }.entries
                    ) { it.key to write(it.value) }.toMap()
                )
            )

        // Before SnapshotMutableState, which each is too, with the structural policy.
        is MutableIntState -> typed("MutableIntState", JsonPrimitive(value.intValue))

        is MutableLongState -> typed("MutableLongState", JsonPrimitive(value.longValue))

        is MutableFloatState -> typed(
            "MutableFloatState",
            JsonPrimitive(value.floatValue.toString())
        )

        is MutableDoubleState ->
            typed("MutableDoubleState", JsonPrimitive(value.doubleValue.toString()))

        is SnapshotMutableState<*> -> {
            val policy = policies.entries.firstOrNull { it.value === value.policy }
                ?: throw IllegalArgumentException("A MutableState's ${value.policy} can't be saved")
            typed("MutableState", JsonArray(listOf(JsonPrimitive(policy.key), write(value.value))))
        }

        else -> throw IllegalArgumentException("${value::class} can't be saved")
    }

    private fun typed(type: String, value: JsonElement) = JsonObject(mapOf(type to value))
}

private fun decode(json: JsonElement): Any? {
    if (json is JsonNull) return null
    val (type, value) = json.jsonObject.entries.single()
    return when (type) {
        "String" -> value.jsonPrimitive.content

        "Boolean" -> value.jsonPrimitive.boolean

        "Char" -> value.jsonPrimitive.content.single()

        "Int" -> value.jsonPrimitive.int

        "Long" -> value.jsonPrimitive.long

        "Float" -> value.jsonPrimitive.content.toFloat()

        "Double" -> value.jsonPrimitive.content.toDouble()

        "BooleanArray" -> value.jsonArray.map { it.jsonPrimitive.boolean }.toBooleanArray()

        "CharArray" -> value.jsonPrimitive.content.toCharArray()

        "IntArray" -> value.jsonArray.map { it.jsonPrimitive.int }.toIntArray()

        "LongArray" -> value.jsonArray.map { it.jsonPrimitive.long }.toLongArray()

        "FloatArray" -> value.jsonArray.map { it.jsonPrimitive.content.toFloat() }.toFloatArray()

        "DoubleArray" -> value.jsonArray.map { it.jsonPrimitive.content.toDouble() }.toDoubleArray()

        "Array" -> value.jsonArray.map(::decode).toTypedArray()

        "SnapshotStateList" -> value.jsonArray.map(::decode).toMutableStateList()

        // Mutable, as they were saved: SaveableStateHolder takes a page's state out of the map it
        // restores.
        "List" -> value.jsonArray.mapTo(mutableListOf(), ::decode)

        "Map" ->
            value.jsonArray.associateTo(mutableMapOf()) {
                decode(it.jsonArray[0]) to decode(it.jsonArray[1])
            }

        "SavedState" -> savedState(value.jsonObject.mapValues { decode(it.value) })

        "MutableIntState" -> mutableIntStateOf(value.jsonPrimitive.int)

        "MutableLongState" -> mutableLongStateOf(value.jsonPrimitive.long)

        "MutableFloatState" -> mutableFloatStateOf(value.jsonPrimitive.content.toFloat())

        "MutableDoubleState" -> mutableDoubleStateOf(value.jsonPrimitive.content.toDouble())

        "MutableState" ->
            mutableStateOf(
                decode(value.jsonArray[1]),
                policies.getValue(value.jsonArray[0].jsonPrimitive.content)
            )

        else -> throw IllegalArgumentException("A saved $type can't be restored")
    }
}

/** The policies a MutableState can be saved with, as on Android. */
private val policies: Map<String, SnapshotMutationPolicy<Any?>> =
    mapOf(
        "structural" to structuralEqualityPolicy(),
        "referential" to referentialEqualityPolicy(),
        "neverEqual" to neverEqualPolicy()
    )
