package io.github.bryancassell.bluecard.testing

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
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
import androidx.test.core.app.ApplicationProvider
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.ui.endsSentence
import io.github.bryancassell.bluecard.ui.joinedAsOne
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

/** Joins a label's parts, as `readAsOneLabel` does. */
private val separator: String
    get() = ApplicationProvider.getApplicationContext<Context>()
        .getString(R.string.read_as_one_separator)

/**
 * Whether something read as one ([onReadAsOne]) shows [text] as one of its lines, or part of
 * one with [substring], and screen readers read it in its label: as a whole part of it, unless
 * [substring].
 */
fun hasLine(text: String, substring: Boolean = false): SemanticsMatcher {
    val reads = if (substring) {
        hasContentDescription(text, substring = true)
    } else {
        // Between the label's ends or the separators around it, which keep only their space
        // after a part that ends a sentence (joinedAsOne).
        val gap = separator.dropWhile { !it.isWhitespace() }
        val before = "(?:^|(?<=${Regex.escape(separator)})|(?<=[.?!]${Regex.escape(gap)}))"
        val after = if (endsSentence(text.last())) gap else separator
        val part = Regex("$before${Regex.escape(text)}(?:$|(?=${Regex.escape(after)}))")
        SemanticsMatcher("reads \"$text\" as a part of its label") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                .any { part.containsMatchIn(it) }
        }
    }
    return hasAnyDescendant(hasText(text, substring)) and reads
}

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
    return assertContentDescriptionEquals(joinedAsOne(lines.toList(), separator))
}

/** Whether a node's click action has [label], which screen readers announce. */
fun hasClickLabel(label: String) = SemanticsMatcher("click label is \"$label\"") {
    it.config.getOrNull(SemanticsActions.OnClick)?.label == label
}
