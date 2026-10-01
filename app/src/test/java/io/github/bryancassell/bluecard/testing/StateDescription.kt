package io.github.bryancassell.bluecard.testing

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher

/** Whether screen readers read [description] as the node's state, such as "Completed". */
fun hasStateDescription(description: String) =
    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, description)
