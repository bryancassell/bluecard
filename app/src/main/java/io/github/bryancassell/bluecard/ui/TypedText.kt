package io.github.bryancassell.bluecard.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.core.text.BidiFormatter

// Text the scout types, such as their name, may be in another language than the app's strings,
// and so in another direction: a Persian name reads right-to-left while the screen is laid out
// left-to-right for English strings. It keeps its own direction, so its punctuation stays at its
// end (see ARCHITECTURE.md, UI layer).

/** The text style for a field the scout types in: its text takes its own direction. */
@Composable
@ReadOnlyComposable
fun typedTextFieldStyle(): TextStyle =
    LocalTextStyle.current.copy(textDirection = TextDirection.Content)

/**
 * [text] the scout typed, to show on its own or inside one of the app's strings. It's wrapped
 * so it keeps its own direction within the strings' language's.
 */
@Composable
@ReadOnlyComposable
fun typedText(text: String): String = BidiFormatter.getInstance(stringsLocale()).unicodeWrap(text)
