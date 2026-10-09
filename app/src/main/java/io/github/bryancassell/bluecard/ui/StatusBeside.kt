package io.github.bryancassell.bluecard.ui

import android.icu.text.BreakIterator
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * Whether a [status], such as "In progress", fits on one line beside [lines] of text, each in its
 * own style, in [width], without a line breaking in the middle of a word. Squeezed narrower than
 * a word, a line breaks it, as "Swimmi" / "ng" did at the largest text and display size (#307),
 * so the status goes under the lines instead.
 */
// Each line is laid out as it would be beside the status, rather than compared with its longest
// word: Compose takes the longest word to end at a hyphen, as in "Eagle-required", where
// Android's line breaker doesn't break without hyphenation, which the app leaves off.
@Composable
fun statusFitsBeside(
    status: String,
    statusStyle: TextStyle,
    lines: List<Pair<String, TextStyle>>,
    width: Dp
): Boolean {
    val measurer = rememberTextMeasurer()
    val besideWidth = with(LocalDensity.current) { width.roundToPx() } -
        measurer.measure(status, statusStyle).size.width
    return besideWidth > 0 && lines.none { (text, style) ->
        measurer.measure(text, style, constraints = Constraints(maxWidth = besideWidth))
            .breaksAWord()
    }
}

/** Whether a line ends where the text has no line break opportunity: in the middle of a word. */
private fun TextLayoutResult.breaksAWord(): Boolean {
    val breaks = BreakIterator.getLineInstance().apply { setText(layoutInput.text.text) }
    return (0 until lineCount - 1).any { !breaks.isBoundary(getLineEnd(it)) }
}
