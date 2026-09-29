package io.github.bryancassell.bluecard.testing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection

/**
 * The direction of this text field's first paragraph, which decides which end a final period
 * goes at. Only text fields report the layout they draw: `Text` reports one laid out again
 * without the layout's direction, so it would read left-to-right for English text even when
 * it's drawn right-to-left.
 */
fun SemanticsNodeInteraction.paragraphDirection(): ResolvedTextDirection {
    val layouts = mutableListOf<TextLayoutResult>()
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    return layouts.single().getParagraphDirection(0)
}
