package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer

/**
 * Replaces each line break in a field's text with a space after every edit, such as a paste.
 * A single-line field (`TextFieldLineLimits.SingleLine`) keeps a line break in its text but
 * doesn't show one: it draws "\n" as a space and "\r" as nothing. The text would then hold a
 * break the scout never saw. "\r\n" counts as one line break, and so does each of Unicode's
 * other line-ending characters.
 */
object LineBreaksAsSpaces : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val text = asCharSequence()
        val first = text.indexOfFirst { it in LINE_BREAK_CHARS }
        if (first == -1) return
        val last = text.indexOfLast { it in LINE_BREAK_CHARS }
        // One replace for all of them. TextFieldBuffer copies the whole text for a replace far
        // from the last one, so replacing each line break of a huge paste would be slow.
        replace(first, last + 1, text.substring(first, last + 1).replace(LineBreak, " "))
    }
}

/**
 * Line feed, vertical tab, form feed, carriage return, next line, and line and paragraph
 * separators.
 */
private const val LINE_BREAK_CHARS = "\n\u000B\u000C\r\u0085\u2028\u2029"

private val LineBreak = Regex("\r\n|[$LINE_BREAK_CHARS]")
