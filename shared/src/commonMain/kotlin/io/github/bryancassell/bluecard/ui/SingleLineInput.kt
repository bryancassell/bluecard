package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.then
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * The input transformation for a single-line field (`TextFieldLineLimits.SingleLine`): keeps
 * it to [maxLength] characters ([rememberTextLengthLimit]), then replaces each line break with a
 * space ([LineBreaksAsSpaces]). The limit comes first, as [LineBreaksAsSpaces] explains. A number
 * field uses [NumberInput] with its own [TextLengthLimit] instead, since [NumberInput] rejects
 * a line break.
 */
@Composable
fun rememberSingleLineInput(maxLength: Int): InputTransformation {
    val limit = rememberTextLengthLimit(maxLength)
    return remember(limit) { limit.then(LineBreaksAsSpaces) }
}
