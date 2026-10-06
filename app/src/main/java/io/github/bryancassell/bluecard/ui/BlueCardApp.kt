package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.bryancassell.bluecard.MainActivityUiState
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.ui.navigation.BlueCardNavDisplay
import io.github.bryancassell.bluecard.ui.theme.BlueCardTheme

/**
 * The app's content. Shows nothing while loading; the splash screen covers it. Tells the scout
 * when their progress was damaged, over whichever screen is open, until they tap OK
 * ([onDismissDamagedProgressNotice]).
 */
@Composable
fun BlueCardApp(uiState: MainActivityUiState, onDismissDamagedProgressNotice: () -> Unit) {
    ProvideStringsLanguageResources {
        BlueCardTheme {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                when (uiState) {
                    // Composed while loading too, with no text, so screen readers hear the
                    // message (see ScreenMessage).
                    MainActivityUiState.Loading, MainActivityUiState.LoadFailed -> ScreenMessage(
                        text = when (uiState) {
                            MainActivityUiState.LoadFailed -> stringResource(R.string.load_failed)
                            else -> null
                        },
                        modifier = Modifier.padding(innerPadding)
                    )

                    is MainActivityUiState.Ready -> {
                        BlueCardNavDisplay(
                            isSetUp = uiState.isSetUp,
                            // Consuming the system bar insets that innerPadding already covers
                            // keeps screens' imePadding() from adding them a second time.
                            modifier = Modifier
                                .padding(innerPadding)
                                .consumeWindowInsets(innerPadding)
                        )
                        if (uiState.showDamagedProgressNotice) {
                            DamagedProgressNotice(onDismiss = onDismissDamagedProgressNotice)
                        }
                    }
                }
            }
        }
    }
}
