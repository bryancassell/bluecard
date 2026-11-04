package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.snapshots.SnapshotMutableState
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.savedState
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
 * [registry] can save the types a SavedState holds, as Android's can save what a Bundle holds,
 * and the Map and MutableState that SaveableStateHolder and rememberSaveable save.
 * `rememberSaveable` refuses any other type as it's first remembered, as on Android.
 *
 * iOS keeps a scene's state when the app is installed over it, even by the same build (checked
 * on an iOS 27.0 simulator), so the text names the [build] that saved it, and state saved by
 * another build is dropped. A page's state could otherwise be read by code that saves it
 * differently, such as a back stack naming a key that was renamed, which Android avoids by
 * dropping an app's saved state when it's updated. Builds with the same version and build number,
 * such as two debug builds, share it.
 */
class SceneSavedState(saved: String, private val build: String) {
    val registry: SaveableStateRegistry = SaveableStateRegistry(restore(saved), ::canBeSaved)

    /** What [registry] holds now, as the text a new [SceneSavedState] restores it from. */
    fun save(): String {
        val values = registry.performSave().mapValues { JsonArray(it.value.map(::encode)) }
        return JsonObject(mapOf(BUILD to JsonPrimitive(build), VALUES to JsonObject(values)))
            .toString()
    }

    /**
     * The values [save] wrote as [saved], or null if there are none, or another build saved
     * them. The build is checked before the values are read, so a later build can save them
     * another way.
     */
    private fun restore(saved: String): Map<String, List<Any?>>? {
        if (saved.isEmpty()) return null
        val state = Json.parseToJsonElement(saved).jsonObject
        if (state.getValue(BUILD).jsonPrimitive.content != build) return null
        return state.getValue(VALUES).jsonObject.mapValues { it.value.jsonArray.map(::decode) }
    }

    private companion object {
        const val BUILD = "build"
        const val VALUES = "values"
    }
}

private fun canBeSaved(value: Any): Boolean = try {
    encode(value)
    true
} catch (_: IllegalArgumentException) {
    false
}

/**
 * [value] as JSON that [decode] reads back as the same type, since a page reads each value back
 * as the type it saved, such as an Int and not a Long. Throws an IllegalArgumentException for a
 * type it can't save.
 */
private fun encode(value: Any?): JsonElement = when (value) {
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

    is FloatArray -> typed("FloatArray", JsonArray(value.map { JsonPrimitive(it.toString()) }))

    is DoubleArray ->
        typed("DoubleArray", JsonArray(value.map { JsonPrimitive(it.toString()) }))

    is Array<*> -> typed("Array", JsonArray(value.map(::encode)))

    is List<*> -> typed("List", JsonArray(value.map(::encode)))

    is Map<*, *> ->
        typed(
            "Map",
            JsonArray(
                value.map {
                    JsonArray(listOf(encode(it.key), encode(it.value)))
                }
            )
        )

    is SavedState ->
        typed("SavedState", JsonObject(value.read { toMap() }.mapValues { encode(it.value) }))

    is SnapshotMutableState<*> -> {
        val policy = policies.entries.firstOrNull { it.value === value.policy }
            ?: throw IllegalArgumentException("A MutableState's ${value.policy} can't be saved")
        typed("MutableState", JsonArray(listOf(JsonPrimitive(policy.key), encode(value.value))))
    }

    else -> throw IllegalArgumentException("${value::class} can't be saved")
}

private fun typed(type: String, value: JsonElement) = JsonObject(mapOf(type to value))

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

        // Mutable, as they were saved: SaveableStateHolder takes a page's state out of the map it
        // restores.
        "List" -> value.jsonArray.mapTo(mutableListOf(), ::decode)

        "Map" ->
            value.jsonArray.associateTo(mutableMapOf()) {
                decode(it.jsonArray[0]) to decode(it.jsonArray[1])
            }

        "SavedState" -> savedState(value.jsonObject.mapValues { decode(it.value) })

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
