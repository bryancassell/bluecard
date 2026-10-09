package io.github.bryancassell.bluecard.ui

import android.icu.text.BreakIterator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints

/**
 * Whether a [status], such as "In progress", fits on one line beside [lines] of text, each in its
 * own style, in [width] pixels, without a line breaking in the middle of a word. Squeezed
 * narrower than a word, a line breaks it, as "Swimmi" / "ng" did at the largest text and display
 * size (#307), so the status goes under the lines instead. The width is in pixels so the caller
 * can take away its padding and gaps rounded as its layout rounds them.
 */
// Each line is laid out as it would be beside the status, rather than compared with its longest
// word: Compose takes the longest word to end at a hyphen, as in "Eagle-required", where
// Android's line breaker doesn't break without hyphenation, which the app leaves off.
@Composable
fun statusFitsBeside(
    status: String,
    statusStyle: TextStyle,
    lines: List<Pair<String, TextStyle>>,
    width: Int
): Boolean {
    val measurer = rememberTextMeasurer()
    // The measurer is new for a new density, font or layout direction.
    return remember(measurer, status, statusStyle, lines, width) {
        val besideWidth = width - measurer.measure(status, statusStyle).size.width
        besideWidth > 0 && lines.none { (text, style) ->
            measurer.measure(text, style, constraints = Constraints(maxWidth = besideWidth))
                .breaksAWord()
        }
    }
}

/** Whether a line ends where the text has no line break opportunity: in the middle of a word. */
private fun TextLayoutResult.breaksAWord(): Boolean {
    if (lineCount == 1) return false
    val breaks = BreakIterator.getLineInstance().apply { setText(layoutInput.text.text) }
    return (0 until lineCount - 1).any { !breaks.isBoundary(getLineEnd(it)) }
}
