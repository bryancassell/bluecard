package io.github.bryancassell.bluecard.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.ui.profile.ProfileFields

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
        name = viewModel.name,
        unitNumber = viewModel.unitNumber,
        onSave = viewModel::save,
        modifier = modifier
    )
}

/** First launch: asks for the scout's [name] and [unitNumber]. */
@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    name: TextFieldState,
    unitNumber: TextFieldState,
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
        ProfileFields(
            name = name,
            unitNumber = unitNumber,
            enabled = uiState.canEdit,
            // Saves after Done closes the keyboard; the ViewModel ignores the save if the form is
            // incomplete.
            onDone = onSave
        )
        Button(onClick = onSave, enabled = uiState.canSave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_save))
        }
        if (uiState.saveStatus == SaveStatus.Failed) {
            val message = stringResource(R.string.onboarding_save_failed)
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                // Read out by screen readers as it appears, like ScreenMessage. A retry that fails
                // too usually isn't: the message is hidden while saving, and whether that's ever
                // drawn depends on timing (#234).
                modifier = Modifier.semantics { paneTitle = message }
            )
        }
    }
}
