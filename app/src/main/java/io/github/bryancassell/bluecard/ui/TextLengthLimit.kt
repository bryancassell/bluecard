package io.github.bryancassell.bluecard.ui

import android.icu.text.BreakIterator
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.delete
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.maxTextLength

/**
 * Keeps a text field to [maxLength] characters by cutting off the end of what an edit adds,
 * such as a long paste, so the text already in the field stays. Typing into a full field
 * does nothing. `InputTransformation.maxLength` rejects the whole edit instead.
 *
 * It cuts only between characters as a reader sees them, never through an emoji made of
 * several code points or a letter with an accent mark, so the text can end a little short of
 * the limit.
 */
class TextLengthLimit(private val maxLength: Int) : InputTransformation {
    override fun SemanticsPropertyReceiver.applySemantics() {
        maxTextLength = maxLength
    }

    override fun TextFieldBuffer.transformInput() {
        if (length <= maxLength) return
        // The edit changed the text between what it kept at the start and at the end.
        // (TextFieldBuffer.changes says exactly where, but it's experimental.)
        val original = originalText
        var kept = 0
        while (kept < minOf(length, original.length) && charAt(kept) == original[kept]) kept++
        var keptAtEnd = 0
        while (keptAtEnd < minOf(length, original.length) - kept &&
            charAt(length - 1 - keptAtEnd) == original[original.length - 1 - keptAtEnd]
        ) {
            keptAtEnd++
        }
        // Widened to whole characters, since an edit can change part of one, such as by
        // adding a skin tone to an emoji.
        val characters = characters()
        cutExtra(
            characters.boundaryAtOrBefore(kept),
            characters.boundaryAtOrAfter(length - keptAtEnd),
            characters
        )
        // Text that was already too long, such as text set in code, is cut at its end.
        if (length > maxLength) cutExtra(0, length, characters())
    }

    /**
     * Deletes the characters past [maxLength] from the end of the text from [start] to [end],
     * which are boundaries between the [characters].
     */
    private fun TextFieldBuffer.cutExtra(start: Int, end: Int, characters: BreakIterator) {
        val extra = length - maxLength
        delete(characters.boundaryAtOrBefore(maxOf(start, end - extra)), end)
    }
}

/** The boundaries between characters as a reader sees them (grapheme clusters). */
private fun TextFieldBuffer.characters(): BreakIterator =
    BreakIterator.getCharacterInstance().apply { setText(this@characters.toString()) }

private fun BreakIterator.boundaryAtOrBefore(offset: Int) =
    if (isBoundary(offset)) offset else preceding(offset)

private fun BreakIterator.boundaryAtOrAfter(offset: Int) =
    if (isBoundary(offset)) offset else following(offset)
