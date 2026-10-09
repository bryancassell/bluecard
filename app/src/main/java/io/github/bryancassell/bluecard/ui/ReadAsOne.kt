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
 * TalkBack a merged node's own properties and its parts as nodes of their own (`getInfoText` in
 * `AndroidComposeViewAccessibilityDelegateCompat`, Compose UI 1.12.1), and marks a part off
 * screen as not visible to the user. TalkBack leaves those parts out, so the rank card and a
 * requirement row partly scrolled off screen were read without their first lines (#305, #312).
 * Compose gives screen readers none of the parts it clears, so a part read as the state, such as
 * a progress bar, needs no `hideFromAccessibility` of its own.
 */
@Composable
@ReadOnlyComposable
fun readAsOneLabel(vararg parts: String?): String =
    joinedAsOne(parts.filterNotNull(), stringResource(R.string.read_as_one_separator))

/**
 * [parts] joined with [separator]. After a part that already ends with the separator's
 * punctuation, such as a requirement's summary ending in ".", only the space after it follows,
 * so the punctuation isn't doubled ("knots.. (2 of 7") on a braille display or when punctuation
 * is read aloud.
 */
internal fun joinedAsOne(parts: List<String>, separator: String): String {
    val punctuation = separator.trimEnd()
    val space = separator.substring(punctuation.length)
    return buildString {
        parts.forEachIndexed { index, part ->
            if (index > 0) {
                val endsWithIt = punctuation.isNotEmpty() && parts[index - 1].endsWith(punctuation)
                append(if (endsWithIt) space else separator)
            }
            append(part)
        }
    }
}
