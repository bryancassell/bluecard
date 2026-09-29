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

/**
 * The text [keepText] kept under [key] when the system stopped the app, or null if none.
 * Anything else under [key] is ignored, such as an extra of the intent that opened the app,
 * which Navigation 3 gives every screen's [SavedStateHandle] as a default argument.
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

private const val TEXT = "text"
