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
        val shorter = minOf(length, original.length)
        var kept = 0
        while (kept < shorter && charAt(kept) == original[kept]) kept++
        var keptAtEnd = 0
        while (keptAtEnd < shorter &&
            charAt(length - 1 - keptAtEnd) == original[original.length - 1 - keptAtEnd]
        ) {
            keptAtEnd++
        }
        // A paste or typing replaces the selection, so if the text before and after the
        // selection is unchanged, the edit is taken to be there. The text alone can't place an
        // edit that starts like the text after it or ends like the text it replaced. (What
        // was before and after the selection can overlap in the new text only when text
        // already too long loses some away from the selection.)
        val selection = originalSelection
        val afterSelection = original.length - selection.max
        if (kept >= selection.min && keptAtEnd >= afterSelection &&
            selection.min + afterSelection <= length
        ) {
            kept = selection.min
            keptAtEnd = afterSelection
        } else {
            // An edit away from the selection, such as autocorrect changing the word before
            // the cursor.
            keptAtEnd = minOf(keptAtEnd, shorter - kept)
        }
        // Cut from the end of what the edit changed, at a boundary between characters.
        val characters = characters()
        val end = characters.boundaryAtOrAfter(length - keptAtEnd)
        val from = characters.boundaryAtOrBefore(maxOf(kept, end - (length - maxLength)))
        if (from < kept) {
            // Only cutting text that was already there would make room, as when an accent is
            // added to the last letter of a full field, so the edit is rejected.
            revertAllChanges()
        } else {
            delete(from, end)
        }
        // Text that was already too long, such as text set in code, is cut at its end.
        if (length > maxLength) delete(characters().boundaryAtOrBefore(maxLength), length)
    }
}

/** The boundaries between characters as a reader sees them (grapheme clusters). */
private fun TextFieldBuffer.characters(): BreakIterator =
    BreakIterator.getCharacterInstance().apply { setText(this@characters.toString()) }

private fun BreakIterator.boundaryAtOrBefore(offset: Int) =
    if (isBoundary(offset)) offset else preceding(offset)

private fun BreakIterator.boundaryAtOrAfter(offset: Int) =
    if (isBoundary(offset)) offset else following(offset)
