package io.github.bryancassell.bluecard.ui.badge

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.then
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.ui.LineBreaksAsSpaces
import io.github.bryancassell.bluecard.ui.LoadFailedMessage
import io.github.bryancassell.bluecard.ui.SaveFailedSnackbarHost
import io.github.bryancassell.bluecard.ui.SaveFailure
import io.github.bryancassell.bluecard.ui.ScreenMessage
import io.github.bryancassell.bluecard.ui.TextLengthLimit
import io.github.bryancassell.bluecard.ui.typedTextFieldStyle

/** Connects the Edit counselor screen to its ViewModel. */
@Composable
fun EditCounselorRoute(
    badgeId: String,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditCounselorViewModel =
        hiltViewModel<EditCounselorViewModel, EditCounselorViewModel.Factory> {
            it.create(badgeId)
        }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    EditCounselorScreen(
        uiState = uiState,
        name = viewModel.name,
        phone = viewModel.phone,
        email = viewModel.email,
        onSave = viewModel::save,
        onSaved = {
            onSaved()
            viewModel.onClosed()
        },
        onSaveFailureShown = viewModel::onSaveFailureShown,
        modifier = modifier
    )
}

/**
 * A badge's merit badge counselor: their [name], [phone] number and [email] address, each
 * optional, which the scout saves when they choose. Once they're saved, [onSaved] closes the
 * page. Leaving it without saving discards the changes.
 */
@Composable
fun EditCounselorScreen(
    uiState: EditCounselorUiState,
    name: TextFieldState,
    phone: TextFieldState,
    email: TextFieldState,
    onSave: () -> Unit,
    onSaved: () -> Unit,
    onSaveFailureShown: (SaveFailure) -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        EditCounselorUiState.Loading -> LoadingIndicator(modifier)

        EditCounselorUiState.LoadFailed -> LoadFailedMessage(modifier)

        EditCounselorUiState.Unavailable ->
            ScreenMessage(stringResource(R.string.requirements_unavailable), modifier)

        // Ends the page above the keyboard, so every field can be scrolled into view.
        is EditCounselorUiState.Ready -> Box(modifier = modifier.imePadding()) {
            if (uiState.saved) {
                val currentOnSaved by rememberUpdatedState(onSaved)
                LaunchedEffect(Unit) { currentOnSaved() }
            }
            CounselorFields(uiState, name, phone, email, onSave)
            SaveFailedSnackbarHost(
                failure = uiState.saveFailure,
                onShown = onSaveFailureShown,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

// Plenty for each field; no email address is longer than 254 characters. The fields' text is
// saved with the screen's state, which has a size limit, so a huge paste mustn't reach it.
private val NameLengthLimit = TextLengthLimit(maxLength = 100)
private val PhoneLengthLimit = TextLengthLimit(maxLength = 50)
private val EmailLengthLimit = TextLengthLimit(maxLength = 254)

@Composable
private fun CounselorFields(
    uiState: EditCounselorUiState.Ready,
    name: TextFieldState,
    phone: TextFieldState,
    email: TextFieldState,
    onSave: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = uiState.badgeName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.edit_counselor_title),
            style = MaterialTheme.typography.headlineMedium,
            // Lets screen reader users jump to it.
            modifier = Modifier.semantics { heading() }
        )
        CounselorField(
            state = name,
            label = R.string.edit_counselor_name,
            lengthLimit = NameLengthLimit,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            )
        )
        CounselorField(
            state = phone,
            label = R.string.edit_counselor_phone,
            lengthLimit = PhoneLengthLimit,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next
            )
        )
        CounselorField(
            state = email,
            label = R.string.edit_counselor_email,
            lengthLimit = EmailLengthLimit,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            )
        )
        Button(
            onClick = {
                onSave()
                // Done editing: closes the keyboard.
                focusManager.clearFocus()
            },
            enabled = uiState.changed,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(stringResource(R.string.edit_counselor_save))
        }
    }
}

@Composable
private fun CounselorField(
    state: TextFieldState,
    @StringRes label: Int,
    lengthLimit: TextLengthLimit,
    keyboardOptions: KeyboardOptions
) {
    OutlinedTextField(
        state = state,
        textStyle = typedTextFieldStyle(),
        label = { Text(stringResource(label)) },
        // Each field is one line, so a pasted line break mustn't reach its text. After the
        // limit, as LineBreaksAsSpaces explains.
        inputTransformation = lengthLimit.then(LineBreaksAsSpaces),
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = keyboardOptions,
        modifier = Modifier.fillMaxWidth()
    )
}
