package io.github.bryancassell.bluecard.testing

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText

/**
 * Checks that screen readers read the button with [text] as [description], once, and as a button
 * (ARCHITECTURE.md, Screen reader labels).
 */
fun SemanticsNodeInteractionsProvider.assertButtonReadOnceAs(text: String, description: String) {
    // Only one: TalkBack would also read a second, such as one set on the button as well.
    onNodeWithContentDescription(description)
        .assertContentDescriptionEquals(description)
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        .assertHasClickAction()
    // TalkBack reads the button's parts in turn, so the description has to be the text's own:
    // one beside the text was read and then the text again after it.
    onNodeWithText(text, useUnmergedTree = true).assertContentDescriptionEquals(description)
}
