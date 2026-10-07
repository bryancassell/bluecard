package io.github.bryancassell.bluecard.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Has screen readers read [description] in place of the text, unless it's null. On a button, it
 * goes on the `Text` inside it: Compose gives TalkBack a button's parts in turn, so a description
 * on the button was read and then the text after it (ARCHITECTURE.md, Screen reader labels).
 */
fun Modifier.readAs(description: String?): Modifier =
    if (description == null) this else semantics { contentDescription = description }
