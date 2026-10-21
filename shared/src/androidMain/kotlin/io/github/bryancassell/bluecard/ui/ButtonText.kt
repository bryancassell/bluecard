package io.github.bryancassell.bluecard.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * A button's [text], which screen readers read as [description] in its place, if there is one.
 * The description is the text's, not the button's: Compose gives TalkBack a button's parts in
 * turn, so one on the button was read and then the text after it (ARCHITECTURE.md, Screen reader
 * labels).
 */
@Composable
fun ButtonText(text: String, description: String?) {
    Text(
        text = text,
        modifier = if (description == null) {
            Modifier
        } else {
            Modifier.semantics { contentDescription = description }
        }
    )
}
