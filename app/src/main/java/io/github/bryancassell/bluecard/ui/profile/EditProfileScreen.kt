package io.github.bryancassell.bluecard.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.ui.LoadFailedMessage
import io.github.bryancassell.bluecard.ui.SaveFailedSnackbarHost
import io.github.bryancassell.bluecard.ui.SaveFailure
import io.github.bryancassell.bluecard.ui.badge.LoadingIndicator

/** Connects the Edit name and unit screen to its ViewModel. */
@Composable
fun EditProfileRoute(
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    EditProfileScreen(
        uiState = uiState,
        name = viewModel.name,
        unitNumber = viewModel.unitNumber,
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
 * The scout's [name] and [unitNumber], both required, which the scout saves when they choose.
 * Once they're saved, [onSaved] closes the page. Leaving it without saving discards the changes.
 */
@Composable
fun EditProfileScreen(
    uiState: EditProfileUiState,
    name: TextFieldState,
    unitNumber: TextFieldState,
    onSave: () -> Unit,
    onSaved: () -> Unit,
    onSaveFailureShown: (SaveFailure) -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        EditProfileUiState.Loading -> LoadingIndicator(modifier)

        EditProfileUiState.LoadFailed -> LoadFailedMessage(modifier)

        // Ends the page above the keyboard, so every field can be scrolled into view.
        is EditProfileUiState.Ready -> Box(modifier = modifier.imePadding()) {
            if (uiState.saved) {
                val currentOnSaved by rememberUpdatedState(onSaved)
                LaunchedEffect(Unit) { currentOnSaved() }
            }
            Fields(uiState, name, unitNumber, onSave)
            SaveFailedSnackbarHost(
                failure = uiState.saveFailure,
                onShown = onSaveFailureShown,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun Fields(
    uiState: EditProfileUiState.Ready,
    name: TextFieldState,
    unitNumber: TextFieldState,
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
            text = stringResource(R.string.edit_profile_title),
            style = MaterialTheme.typography.headlineMedium,
            // Lets screen reader users jump to it.
            modifier = Modifier.semantics { heading() }
        )
        ProfileFields(name = name, unitNumber = unitNumber)
        Button(
            onClick = {
                onSave()
                // Done editing: closes the keyboard.
                focusManager.clearFocus()
            },
            enabled = uiState.canSave,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(stringResource(R.string.edit_profile_save))
        }
    }
}
