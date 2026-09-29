package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.text.input.KeyboardType

/**
 * Keeps a text field to a number: digits, with at most one decimal separator, whichever the
 * keyboard offers. An edit that would make anything else, such as a paste with words in it, is
 * rejected. Digits of any script are kept, as typed.
 */
object NumberInput : InputTransformation {
    override val keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    /** A point, a comma, and the Arabic decimal separator that Persian keyboards offer. */
    private val separators = setOf('.', ',', '\u066B')

    override fun TextFieldBuffer.transformInput() {
        val text = asCharSequence()
        if (text.count { it in separators } > 1 ||
            text.any { !it.isDigit() && it !in separators }
        ) {
            revertAllChanges()
        }
    }
}
