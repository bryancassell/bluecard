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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.edit_profile_save
import io.github.bryancassell.bluecard.resources.edit_profile_title
import io.github.bryancassell.bluecard.resources.load_failed
import io.github.bryancassell.bluecard.resources.save_failed
import io.github.bryancassell.bluecard.ui.ConfirmDiscardOnBack
import io.github.bryancassell.bluecard.ui.KeepInViewWhileFocused
import io.github.bryancassell.bluecard.ui.ScreenLoadingIndicator
import io.github.bryancassell.bluecard.ui.ScreenMessage
import io.github.bryancassell.bluecard.ui.TaskFailure
import io.github.bryancassell.bluecard.ui.TaskFailureSnackbarHost
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Connects the Edit name and unit screen to its ViewModel. */
@Composable
fun EditProfileRoute(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditProfileViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    EditProfileScreen(
        uiState = uiState,
        name = viewModel.name,
        unitNumber = viewModel.unitNumber,
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
 * The scout's [name] and [unitNumber], both required, which the scout saves when they choose.
 * Once they're saved, [onSaved] closes the page. Back with unsaved changes asks first, then
 * [onDiscard] closes it without saving them.
 */
@Composable
fun EditProfileScreen(
    uiState: EditProfileUiState,
    name: TextFieldState,
    unitNumber: TextFieldState,
    onSave: () -> Unit,
    onSaved: () -> Unit,
    onDiscard: () -> Unit,
    onSaveFailureShown: (TaskFailure) -> Unit,
    modifier: Modifier = Modifier
) {
    ConfirmDiscardOnBack(
        changed = (uiState as? EditProfileUiState.Ready)?.changed == true,
        onDiscard = onDiscard
    )
    when (uiState) {
        EditProfileUiState.Loading -> ScreenLoadingIndicator(modifier)

        EditProfileUiState.LoadFailed ->
            ScreenMessage(stringResource(Res.string.load_failed), modifier)

        // Ends the page above the keyboard, so every field can be scrolled into view.
        is EditProfileUiState.Ready -> Box(modifier = modifier.imePadding()) {
            if (uiState.saved) {
                val currentOnSaved by rememberUpdatedState(onSaved)
                LaunchedEffect(Unit) { currentOnSaved() }
            }
            Fields(uiState, name, unitNumber, onSave)
            TaskFailureSnackbarHost(
                failure = uiState.saveFailure,
                message = stringResource(Res.string.save_failed),
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
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 16.dp, top = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.edit_profile_title),
            style = MaterialTheme.typography.headlineMedium,
            // Lets screen reader users jump to it.
            modifier = Modifier.semantics { heading() }
        )
        ProfileNameField(name = name)
        // Keeps Save above the keyboard while the scout types in the unit number: at the
        // largest text and display size, its bottom edge was behind it (#333).
        KeepInViewWhileFocused(scrollState) {
            // Done only closes the keyboard, where Onboarding's also saves: as on the
            // counselor's page, the scout decides when to save, since saving closes the page.
            UnitNumberField(unitNumber = unitNumber)
            Button(
                onClick = {
                    onSave()
                    // Done editing: closes the keyboard.
                    focusManager.clearFocus()
                },
                enabled = uiState.canSave,
                // Here, not on the Column, so the page's margin under Save comes into view
                // with it.
                modifier = Modifier.align(Alignment.End).padding(bottom = 16.dp)
            ) {
                Text(stringResource(Res.string.edit_profile_save))
            }
        }
    }
}
