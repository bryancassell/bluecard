package io.github.bryancassell.bluecard.testing

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.test.core.app.ApplicationProvider
import io.github.bryancassell.bluecard.R
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
 * Whether something read as one ([onReadAsOne]) reads [text] as a whole part of its label:
 * between the label's ends or the separators around it. After a part ending in the separator's
 * punctuation, as "knots." does, only the separator's space follows it.
 */
fun readsLine(text: String): SemanticsMatcher {
    val separator = ApplicationProvider.getApplicationContext<Context>()
        .getString(R.string.read_as_one_separator)
    val punctuation = separator.trimEnd()
    val space = separator.substring(punctuation.length)
    val after = if (text.endsWith(punctuation)) space else separator
    val before = "(?:^|(?<=${Regex.escape(separator)}))"
    val part = Regex("$before${Regex.escape(text)}(?:$|(?=${Regex.escape(after)}))")
    return SemanticsMatcher("reads \"$text\" as a part of its label") { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            .any { part.containsMatchIn(it) }
    }
}

/**
 * Whether something read as one ([onReadAsOne]) shows [text] as one of its lines and reads it
 * as a part of its label ([readsLine]), or, with [substring], shows and reads it in part of
 * one.
 */
fun hasLine(text: String, substring: Boolean = false): SemanticsMatcher =
    hasAnyDescendant(hasText(text, substring)) and if (substring) {
        hasContentDescription(text, substring = true)
    } else {
        readsLine(text)
    }

/**
 * Whether something read as one ([onReadAsOne]) neither shows [text] as one of its lines nor
 * reads it as a part of its label, or, with [substring], in any part of one.
 */
fun hasNoLine(text: String, substring: Boolean = false): SemanticsMatcher =
    !hasAnyDescendant(hasText(text, substring)) and if (substring) {
        !hasContentDescription(text, substring = true)
    } else {
        !readsLine(text)
    }

/** Checks that something read as one ([onReadAsOne]) shows exactly [lines], in order. */
fun SemanticsNodeInteraction.assertShows(vararg lines: String): SemanticsNodeInteraction {
    fun shown(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap(::shown)
    assertEquals(lines.toList(), shown(fetchSemanticsNode()))
    return this
}

/** Whether a node's click action has [label], which screen readers announce. */
fun hasClickLabel(label: String) = SemanticsMatcher("click label is \"$label\"") {
    it.config.getOrNull(SemanticsActions.OnClick)?.label == label
}
