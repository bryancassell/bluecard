package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import io.github.bryancassell.bluecard.R

/**
 * The label of something screen readers read as one, such as a list row: [parts] in reading
 * order, leaving out any that are null. The parts are the text it shows, or what stands in for
 * something it can't show in words, such as the rank card's trail, but not what it reads as its
 * state. It's set with `clearAndSetSemantics`, in place of its parts' text: Compose gives
 * TalkBack a merged node's parts as nodes of their own, and TalkBack leaves out those scrolled
 * off screen (ARCHITECTURE.md, Screen reader labels).
 */
@Composable
@ReadOnlyComposable
fun readAsOneLabel(vararg parts: String?): String =
    joinedAsOne(parts.filterNotNull(), stringResource(R.string.read_as_one_separator))

/**
 * [parts] joined with [separator]. After a part that already ends a sentence, such as a
 * requirement's summary, the separator's punctuation is left out, so it isn't doubled ("knots..")
 * on a braille display or with punctuation read aloud.
 */
internal fun joinedAsOne(parts: List<String>, separator: String): String = buildString {
    parts.forEachIndexed { index, part ->
        if (index > 0) {
            val afterSentence = parts[index - 1].lastOrNull()?.let(::endsSentence) == true
            append(if (afterSentence) separator.dropWhile { !it.isWhitespace() } else separator)
        }
        append(part)
    }
}

/** Whether [char] ends a sentence. */
internal fun endsSentence(char: Char): Boolean = char in ".?!"
