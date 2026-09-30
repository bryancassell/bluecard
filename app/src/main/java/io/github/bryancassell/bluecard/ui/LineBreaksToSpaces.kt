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

/**
 * A line break: "\r\n", or any one of the characters Unicode counts as a line break, such as a
 * form feed pasted from a document.
 */
private val LineBreak = Regex("""\R""")
