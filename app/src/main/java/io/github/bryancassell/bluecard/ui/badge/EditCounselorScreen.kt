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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import io.github.bryancassell.bluecard.data.progress.COUNSELOR_EMAIL_MAX_LENGTH
import io.github.bryancassell.bluecard.data.progress.COUNSELOR_NAME_MAX_LENGTH
import io.github.bryancassell.bluecard.data.progress.COUNSELOR_PHONE_MAX_LENGTH
import io.github.bryancassell.bluecard.ui.ConfirmDiscardOnBack
import io.github.bryancassell.bluecard.ui.LoadingOrMessage
import io.github.bryancassell.bluecard.ui.TaskFailure
import io.github.bryancassell.bluecard.ui.TaskFailureSnackbarHost
import io.github.bryancassell.bluecard.ui.singleLineInput
import io.github.bryancassell.bluecard.ui.typedTextFieldStyle

/** Connects the Edit counselor screen to its ViewModel. */
@Composable
fun EditCounselorRoute(
    badgeId: String,
    onClose: () -> Unit,
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
            onClose()
            viewModel.onClosed()
        },
        onDiscard = onClose,
        onSaveFailureShown = viewModel::onSaveFailureShown,
        modifier = modifier
    )
}

/**
 * A badge's merit badge counselor: their [name], [phone] number and [email] address, each
 * optional, which the scout saves when they choose. Once they're saved, [onSaved] closes the
 * page. Back with unsaved changes asks first, then [onDiscard] closes it without saving them.
 */
@Composable
fun EditCounselorScreen(
    uiState: EditCounselorUiState,
    name: TextFieldState,
    phone: TextFieldState,
    email: TextFieldState,
    onSave: () -> Unit,
    onSaved: () -> Unit,
    onDiscard: () -> Unit,
    onSaveFailureShown: (TaskFailure) -> Unit,
    modifier: Modifier = Modifier
) {
    ConfirmDiscardOnBack(
        changed = (uiState as? EditCounselorUiState.Ready)?.changed == true,
        onDiscard = onDiscard
    )
    when (uiState) {
        // One branch, so screen readers hear the message (see LoadingOrMessage).
        EditCounselorUiState.Loading,
        EditCounselorUiState.LoadFailed,
        EditCounselorUiState.Unavailable -> LoadingOrMessage(
            message = when (uiState) {
                EditCounselorUiState.LoadFailed -> stringResource(R.string.load_failed)

                EditCounselorUiState.Unavailable ->
                    stringResource(R.string.requirements_unavailable)

                else -> null
            },
            modifier = modifier
        )

        // Ends the page above the keyboard, so every field can be scrolled into view.
        is EditCounselorUiState.Ready -> Box(modifier = modifier.imePadding()) {
            if (uiState.saved) {
                val currentOnSaved by rememberUpdatedState(onSaved)
                LaunchedEffect(Unit) { currentOnSaved() }
            }
            CounselorFields(uiState, name, phone, email, onSave)
            TaskFailureSnackbarHost(
                failure = uiState.saveFailure,
                message = stringResource(R.string.save_failed),
                onShown = onSaveFailureShown,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

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
            maxLength = COUNSELOR_NAME_MAX_LENGTH,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            )
        )
        CounselorField(
            state = phone,
            label = R.string.edit_counselor_phone,
            maxLength = COUNSELOR_PHONE_MAX_LENGTH,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next
            )
        )
        CounselorField(
            state = email,
            label = R.string.edit_counselor_email,
            maxLength = COUNSELOR_EMAIL_MAX_LENGTH,
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
    maxLength: Int,
    keyboardOptions: KeyboardOptions
) {
    val input = remember(maxLength) { singleLineInput(maxLength) }
    OutlinedTextField(
        state = state,
        textStyle = typedTextFieldStyle(),
        label = { Text(stringResource(label)) },
        inputTransformation = input,
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = keyboardOptions,
        modifier = Modifier.fillMaxWidth()
    )
}
