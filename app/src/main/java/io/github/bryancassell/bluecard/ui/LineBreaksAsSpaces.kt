package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer

/**
 * Replaces each line break in an edit, such as a paste, with a space. A single-line field
 * (`TextFieldLineLimits.SingleLine`) only draws a line break as a space but keeps it in its
 * text, which would then hold a break the scout never saw. "\r\n" counts as one line break.
 */
object LineBreaksAsSpaces : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        var i = 0
        while (i < length) {
            val char = charAt(i)
            if (char == '\r' || char == '\n') {
                val isCrLf = char == '\r' && i + 1 < length && charAt(i + 1) == '\n'
                replace(i, if (isCrLf) i + 2 else i + 1, " ")
            }
            i++
        }
    }
}
