package io.github.bryancassell.bluecard.testing

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher

/**
 * A node that's a pane titled with its own text, which screen readers announce as it appears
 * (see `ScreenMessage`).
 */
val isPaneTitledWithItsText = SemanticsMatcher("is a pane titled with its own text") { node ->
    val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
    text != null && node.config.getOrNull(SemanticsProperties.PaneTitle) == text
}
