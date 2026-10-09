package io.github.bryancassell.bluecard.testing

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import org.junit.Assert.assertEquals

/**
 * The row, or other button read as one with a label of its own (`readAsOneLabel`), that shows
 * [text] as one of its lines, or part of one with [substring]. It's found in the unmerged tree,
 * which keeps its lines' text: its own semantics have only its label. Clicking it touches its
 * middle, as a tap would.
 */
fun SemanticsNodeInteractionsProvider.onReadAsOne(
    text: String,
    substring: Boolean = false
): SemanticsNodeInteraction =
    onNode(hasClickAction() and hasAnyDescendant(hasText(text, substring)), useUnmergedTree = true)

/**
 * Whether something read as one ([onReadAsOne]) shows [text] as one of its lines, or part of
 * one with [substring], and screen readers read it in its label.
 */
fun hasLine(text: String, substring: Boolean = false): SemanticsMatcher =
    hasAnyDescendant(hasText(text, substring)) and hasContentDescription(text, substring = true)

/**
 * Whether something read as one ([onReadAsOne]) neither shows [text] in any of its lines nor
 * reads it in its label.
 */
fun hasNoLineWith(text: String): SemanticsMatcher =
    !hasAnyDescendant(hasText(text, substring = true)) and
        !hasContentDescription(text, substring = true)

/**
 * Checks that something read as one ([onReadAsOne]) shows exactly [lines], in order, and that
 * screen readers read them in turn as its label.
 */
fun SemanticsNodeInteraction.assertShowsAndReads(vararg lines: String): SemanticsNodeInteraction {
    fun shown(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap(::shown)
    assertEquals(lines.toList(), shown(fetchSemanticsNode()))
    return assertContentDescriptionEquals(lines.joinToString(". "))
}
