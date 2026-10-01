package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.then

/**
 * The input transformation for a single-line field (`TextFieldLineLimits.SingleLine`): keeps
 * it to [maxLength] characters ([TextLengthLimit]), then replaces each line break with a space
 * ([LineBreaksAsSpaces]). The limit comes first, as [LineBreaksAsSpaces] explains. A number
 * field uses [NumberInput] with its own [TextLengthLimit] instead, since [NumberInput] rejects
 * a line break.
 */
fun singleLineInput(maxLength: Int): InputTransformation =
    TextLengthLimit(maxLength).then(LineBreaksAsSpaces)
