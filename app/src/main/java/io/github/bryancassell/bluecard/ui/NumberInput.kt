package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.text.input.KeyboardType

/**
 * Keeps a text field to a number: digits, with at most one decimal separator, a point or a
 * comma, as the keyboard offers it. An edit that would make anything else, such as a paste
 * with words in it, is rejected. Digits of any script are kept, as typed.
 */
object NumberInput : InputTransformation {
    override val keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    override fun TextFieldBuffer.transformInput() {
        val text = asCharSequence()
        val separators = text.count { it == '.' || it == ',' }
        if (separators > 1 || text.any { !it.isDigit() && it != '.' && it != ',' }) {
            revertAllChanges()
        }
    }
}
