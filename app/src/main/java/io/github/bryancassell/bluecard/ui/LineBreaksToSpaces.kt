package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer

/**
 * Keeps a single-line field's text on one line by turning each line break in it, such as one in
 * a pasted signature, into a space. `TextFieldLineLimits.SingleLine` only draws a line break as a
 * space: the field's text keeps it.
 */
object LineBreaksToSpaces : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        // From the end, so replacing one doesn't move those still to come.
        LineBreak.findAll(toString()).toList().asReversed().forEach {
            replace(it.range.first, it.range.last + 1, " ")
        }
    }
}

/** A line break, counting "\r\n" as one. */
private val LineBreak = Regex("\r\n|[\n\r\u0085  ]")
