package io.github.bryancassell.bluecard.testing

import android.icu.text.BreakIterator
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertTrue

/**
 * Checks that this text wraps only where a line may break, such as between words or after a
 * hyphen, and not in the middle of a word, as text squeezed narrower than a word is (#307). Find
 * it in the unmerged tree, where it's a node of its own.
 */
fun SemanticsNodeInteraction.assertNoWordBroken(): SemanticsNodeInteraction {
    val layouts = mutableListOf<TextLayoutResult>()
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    val layout = layouts.single()
    val text = layout.layoutInput.text.text
    val breaks = BreakIterator.getLineInstance().apply { setText(text) }
    for (line in 0 until layout.lineCount - 1) {
        val end = layout.getLineEnd(line)
        assertTrue(
            "\"$text\" breaks mid-word: \"${text.take(end)}\" / \"${text.drop(end)}\"",
            breaks.isBoundary(end)
        )
    }
    return this
}
