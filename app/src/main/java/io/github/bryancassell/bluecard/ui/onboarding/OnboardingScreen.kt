package io.github.bryancassell.bluecard.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.R

/**
 * Connects the Onboarding screen to its ViewModel. Once the profile is saved, the
 * navigation root replaces this screen with Home.
 */
@Composable
fun OnboardingRoute(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    OnboardingScreen(
        uiState = uiState,
        onNameChange = viewModel::onNameChange,
        onUnitNumberChange = viewModel::onUnitNumberChange,
        onSave = viewModel::save,
        modifier = modifier
    )
}

/** First launch: asks for the scout's name and unit number. */
@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    onNameChange: (String) -> Unit,
    onUnitNumberChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        // imePadding() before verticalScroll() shrinks the scrollable area to the space
        // above the keyboard, so every field and the button can be scrolled into view.
        modifier = modifier
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(text = stringResource(R.string.onboarding_message))
        OutlinedTextField(
            value = uiState.name,
            onValueChange = onNameChange,
            enabled = uiState.canEdit,
            label = { Text(stringResource(R.string.onboarding_name)) },
            supportingText = { Text(stringResource(R.string.onboarding_required)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = uiState.unitNumber,
            onValueChange = onUnitNumberChange,
            enabled = uiState.canEdit,
            label = { Text(stringResource(R.string.onboarding_unit_number)) },
            supportingText = { Text(stringResource(R.string.onboarding_required)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            // Save when the form is complete; otherwise just close the keyboard, as Done
            // normally does.
            keyboardActions = KeyboardActions(onDone = {
                if (uiState.canSave) onSave() else defaultKeyboardAction(ImeAction.Done)
            }),
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = onSave, enabled = uiState.canSave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_save))
        }
        if (uiState.saveStatus == SaveStatus.Failed) {
            Text(
                text = stringResource(R.string.onboarding_save_failed),
                color = MaterialTheme.colorScheme.error,
                // Read out by screen readers when it appears.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}
