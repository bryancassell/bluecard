package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A message shown in place of a screen's content, such as when its data couldn't be loaded.
 *
 * Screen readers announce it as it appears, because it's a pane titled with its own text:
 * Compose tells accessibility services that a pane appeared whenever a node with a pane title
 * appears (`updateSemanticsNodesCopyAndPanes` in `AndroidComposeViewAccessibilityDelegateCompat`,
 * Compose UI 1.12.1). Why it isn't a live region is in ARCHITECTURE.md (Load and save failures).
 *
 * Don't put it inside a node that merges its descendants, such as a clickable card: a pane title
 * can't be merged, and Compose throws when it tries, such as when a screen reader is on.
 */
@Composable
fun ScreenMessage(text: String, modifier: Modifier = Modifier) {
    Text(text = text, modifier = modifier.padding(16.dp).semantics { paneTitle = text })
}
