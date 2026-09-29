package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.delete
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.maxTextLength

/**
 * Keeps a text field to [maxLength] characters by cutting off the end of what an edit adds,
 * such as a long paste, so the text already in the field stays. Typing into a full field
 * does nothing. `InputTransformation.maxLength` rejects the whole edit instead.
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
        cutExtra(kept, length - keptAtEnd)
        // Text that was already too long, such as text set in code, is cut at its end.
        cutExtra(0, length)
    }

    /**
     * Deletes the characters past [maxLength] from the end of the text from [start] to [end].
     * It never splits a character made of two UTF-16 units, such as an emoji, so it may leave
     * the text one short of the limit.
     */
    private fun TextFieldBuffer.cutExtra(start: Int, end: Int) {
        val extra = length - maxLength
        if (extra <= 0) return
        var from = maxOf(start, end - extra)
        if (from > start && Character.isHighSurrogate(charAt(from - 1))) from--
        delete(from, end)
    }
}
