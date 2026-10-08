package io.github.bryancassell.bluecard.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.ui.TaskFailure
import io.github.bryancassell.bluecard.ui.TaskFailureSnackbarHost
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
        onSaveFailureShown = viewModel::onSaveFailureShown,
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
    onSaveFailureShown: (TaskFailure) -> Unit,
    modifier: Modifier = Modifier
) {
    // Ends the page above the keyboard, so every field and the button can be scrolled into
    // view, and the snackbar shows above the keyboard.
    Box(modifier = modifier.imePadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                // Saves after Done closes the keyboard; the ViewModel ignores the save if the
                // form is incomplete.
                onDone = onSave
            )
            Button(
                onClick = onSave,
                enabled = uiState.canSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.onboarding_save))
            }
            Text(
                text = stringResource(R.string.onboarding_not_affiliated),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TaskFailureSnackbarHost(
            failure = uiState.saveFailure,
            message = stringResource(R.string.save_failed),
            onShown = onSaveFailureShown,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
