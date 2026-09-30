package io.github.bryancassell.bluecard.ui

import android.os.Bundle
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.core.os.bundleOf
import androidx.lifecycle.SavedStateHandle

/**
 * A text field's state whose text is kept if the system stops the app: it starts with the text
 * kept under [key], if any, and [keepText] keeps its text there from then on.
 */
fun SavedStateHandle.textFieldState(key: String): TextFieldState =
    TextFieldState(restoredText(key).orEmpty()).also { keepText(key, it) }

/**
 * The text [keepText] kept under [key] when the system stopped the app, or null if none.
 * A value under [key] that isn't the kind of Bundle [keepText] keeps is ignored. This is a
 * backstop: MainActivity keeps the extras of the intent that opened the app out of the
 * [SavedStateHandle]s BlueCard creates, but one created another way could still get them.
 */
fun SavedStateHandle.restoredText(key: String): String? =
    (get<Any?>(key) as? Bundle)?.getString(TEXT)

/**
 * Keeps [state]'s text under [key] if the system stops the app. The text is read each time the
 * system saves state, so a change is kept even if nothing observes the field. Navigation 3
 * saves a screen's state once when it leaves the display, and not again while it's in the back
 * stack, so a change made after that isn't kept.
 */
fun SavedStateHandle.keepText(key: String, state: TextFieldState) {
    setSavedStateProvider(key) { bundleOf(TEXT to state.text.toString()) }
}

/**
 * Text fields that start as stored text, such as a saved comment, once it loads ([loadOnce]), or
 * as the text the system stopped the app with. Their text is kept, as by [keepText], only once
 * the stored text has loaded into them, so if the system stops the app before then, the screen
 * loads the stored text again instead of restoring empty fields. The fields, under [keys], are
 * kept and restored together.
 */
class StoredTextFields(private val savedStateHandle: SavedStateHandle, vararg keys: String) {
    private val fields: Map<String, TextFieldState>

    /** Whether the fields hold the stored text, or the text restored in its place. */
    private var loaded: Boolean

    init {
        val restored = keys.associateWith { savedStateHandle.restoredText(it) }
        fields = restored.mapValues { TextFieldState(it.value.orEmpty()) }
        // All are kept together, so if one wasn't restored, such as when another value is under
        // its key, the stored text is loaded into all of them.
        loaded = restored.values.all { it != null }
        if (loaded) keep()
    }

    /** The field under [key], which its text field edits directly. */
    operator fun get(key: String): TextFieldState = fields.getValue(key)

    /**
     * Puts the [stored] text, by key, in the fields and keeps them from then on, unless it's been
     * done already or the fields were restored instead. [stored] is called only when it's done.
     */
    fun loadOnce(stored: () -> Map<String, String?>) {
        if (loaded) return
        loaded = true
        keep()
        val text = stored()
        // In a snapshot of its own, so the fields' observers, such as a ViewModel's uiState, see
        // the change as soon as it's applied, not when Compose next applies changes made outside a
        // snapshot.
        Snapshot.withMutableSnapshot {
            fields.forEach { (key, field) -> field.setTextAndPlaceCursorAtEnd(text[key].orEmpty()) }
        }
    }

    private fun keep() = fields.forEach { (key, field) -> savedStateHandle.keepText(key, field) }
}

private const val TEXT = "text"
