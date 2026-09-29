package io.github.bryancassell.bluecard.ui

import android.os.Bundle
import androidx.compose.foundation.text.input.TextFieldState
import androidx.core.os.bundleOf
import androidx.lifecycle.SavedStateHandle

/**
 * A text field's state whose text is kept if the system stops the app: it starts with the text
 * kept under [key], if any, and [keepText] keeps its text there from then on.
 */
fun SavedStateHandle.textFieldState(key: String): TextFieldState =
    TextFieldState(restoredText(key).orEmpty()).also { keepText(key, it) }

/** The text [keepText] kept under [key] when the system stopped the app, or null if none. */
fun SavedStateHandle.restoredText(key: String): String? = get<Bundle>(key)?.getString(TEXT)

/**
 * Keeps [state]'s text under [key] if the system stops the app. The text is read only when the
 * system saves state, so every change is kept, even one made while nothing observes the field.
 */
fun SavedStateHandle.keepText(key: String, state: TextFieldState) {
    setSavedStateProvider(key) { bundleOf(TEXT to state.text.toString()) }
}

private const val TEXT = "text"
