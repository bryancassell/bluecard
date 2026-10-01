package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer

/**
 * Replaces each line break in a field's text with a space after every edit, such as a paste.
 * A single-line field (`TextFieldLineLimits.SingleLine`) keeps a line break in its text but
 * doesn't show one: it draws "\n" as a space and "\r" as nothing. The text would then hold a
 * break the scout never saw. "\r\n" counts as one line break, and so does each of Unicode's
 * other line-ending characters.
 *
 * [singleLineInput] chains it after a [TextLengthLimit], so the limit cuts a huge paste before
 * this replaces its line breaks one at a time, which keeps the cursor in place but copies the
 * whole text for each break far from the last.
 */
object LineBreaksAsSpaces : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        var i = 0
        while (i < length) {
            val char = charAt(i)
            if (char in LINE_BREAK_CHARS) {
                val isCrLf = char == '\r' && i + 1 < length && charAt(i + 1) == '\n'
                replace(i, if (isCrLf) i + 2 else i + 1, " ")
            }
            i++
        }
    }
}

/**
 * Line feed, vertical tab, form feed, carriage return, next line, and line and paragraph
 * separators.
 */
private const val LINE_BREAK_CHARS = "\n\u000B\u000C\r\u0085\u2028\u2029"
