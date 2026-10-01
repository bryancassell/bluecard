package io.github.bryancassell.bluecard.ui.profile

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.data.profile.PROFILE_NAME_MAX_LENGTH
import io.github.bryancassell.bluecard.data.profile.UNIT_NUMBER_MAX_LENGTH
import io.github.bryancassell.bluecard.ui.singleLineInput
import io.github.bryancassell.bluecard.ui.typedTextFieldStyle

private val NameInput = singleLineInput(maxLength = PROFILE_NAME_MAX_LENGTH)
private val UnitNumberInput = singleLineInput(maxLength = UNIT_NUMBER_MAX_LENGTH)

/**
 * The scout's [name] and [unitNumber] fields, both required, as Onboarding and Edit name and
 * unit show them, one after the other in the caller's column. The keyboard's Done on the unit
 * number closes the keyboard, then calls [onDone].
 */
@Composable
fun ProfileFields(
    name: TextFieldState,
    unitNumber: TextFieldState,
    enabled: Boolean = true,
    onDone: () -> Unit = {}
) {
    OutlinedTextField(
        state = name,
        enabled = enabled,
        textStyle = typedTextFieldStyle(),
        label = { Text(stringResource(R.string.profile_name)) },
        supportingText = { Text(stringResource(R.string.profile_required)) },
        inputTransformation = NameInput,
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Next
        ),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        state = unitNumber,
        enabled = enabled,
        textStyle = typedTextFieldStyle(),
        label = { Text(stringResource(R.string.profile_unit_number)) },
        supportingText = { Text(stringResource(R.string.profile_required)) },
        inputTransformation = UnitNumberInput,
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        onKeyboardAction = { performDefaultAction ->
            performDefaultAction()
            onDone()
        },
        modifier = Modifier.fillMaxWidth()
    )
}
