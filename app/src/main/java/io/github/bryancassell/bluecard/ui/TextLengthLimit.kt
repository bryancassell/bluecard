package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.delete
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.maxTextLength

/**
 * Keeps a text field to [maxLength] characters by cutting off the end of a longer edit, such
 * as a long paste. `InputTransformation.maxLength` rejects the whole edit instead.
 */
class TextLengthLimit(private val maxLength: Int) : InputTransformation {
    override fun SemanticsPropertyReceiver.applySemantics() {
        maxTextLength = maxLength
    }

    override fun TextFieldBuffer.transformInput() {
        if (length > maxLength) delete(maxLength, length)
    }
}
