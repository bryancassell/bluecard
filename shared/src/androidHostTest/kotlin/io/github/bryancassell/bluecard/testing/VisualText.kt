package io.github.bryancassell.bluecard.testing

import android.icu.text.Bidi
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction

/**
 * This node's text in the order it's drawn, from left to right, in a left-to-right paragraph,
 * as the app lays out its English strings. It shows which end of a right-to-left run its
 * punctuation lands at, which a `Text`'s reported layout can't (see [paragraphDirection]).
 * Bidi control characters are left out.
 */
fun SemanticsNodeInteraction.visualText(): String {
    val text = fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }
    val bidi = Bidi().apply { setPara(text, Bidi.LTR, null) }
    return bidi.writeReordered(Bidi.REMOVE_BIDI_CONTROLS.toInt())
}
