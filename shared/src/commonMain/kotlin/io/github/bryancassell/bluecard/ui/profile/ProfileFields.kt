package io.github.bryancassell.bluecard.ui.profile

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.github.bryancassell.bluecard.data.profile.PROFILE_NAME_MAX_LENGTH
import io.github.bryancassell.bluecard.data.profile.UNIT_NUMBER_MAX_LENGTH
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.profile_name
import io.github.bryancassell.bluecard.resources.profile_required
import io.github.bryancassell.bluecard.resources.profile_unit_number
import io.github.bryancassell.bluecard.ui.rememberSingleLineInput
import io.github.bryancassell.bluecard.ui.typedTextFieldStyle
import org.jetbrains.compose.resources.stringResource

/**
 * The scout's [name] field, required, as Onboarding and Edit name and unit show it. Its
 * keyboard's action is Next, since both pages put [UnitNumberField] next.
 */
@Composable
fun ProfileNameField(name: TextFieldState, enabled: Boolean = true) {
    OutlinedTextField(
        state = name,
        enabled = enabled,
        textStyle = typedTextFieldStyle(),
        label = { Text(stringResource(Res.string.profile_name)) },
        supportingText = { Text(stringResource(Res.string.profile_required)) },
        inputTransformation = rememberSingleLineInput(maxLength = PROFILE_NAME_MAX_LENGTH),
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Next
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * The scout's [unitNumber] field, required, under [ProfileNameField]. It's apart from the name
 * so that Edit name and unit can keep it in view with Save. The keyboard's Done closes the
 * keyboard, then calls [onDone].
 */
@Composable
fun UnitNumberField(unitNumber: TextFieldState, enabled: Boolean = true, onDone: () -> Unit = {}) {
    OutlinedTextField(
        state = unitNumber,
        enabled = enabled,
        textStyle = typedTextFieldStyle(),
        label = { Text(stringResource(Res.string.profile_unit_number)) },
        supportingText = { Text(stringResource(Res.string.profile_required)) },
        inputTransformation = rememberSingleLineInput(maxLength = UNIT_NUMBER_MAX_LENGTH),
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        onKeyboardAction = { performDefaultAction ->
            performDefaultAction()
            onDone()
        },
        modifier = Modifier.fillMaxWidth()
    )
}
