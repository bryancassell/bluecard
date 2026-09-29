package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.bryancassell.bluecard.MainActivityUiState
import io.github.bryancassell.bluecard.ui.navigation.BlueCardNavDisplay
import io.github.bryancassell.bluecard.ui.theme.BlueCardTheme

/** The app's content. Shows nothing while loading; the splash screen covers it. */
@Composable
fun BlueCardApp(uiState: MainActivityUiState) {
    ProvideStringsLanguageResources {
        BlueCardTheme {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                when (uiState) {
                    MainActivityUiState.Loading -> Unit

                    MainActivityUiState.LoadFailed ->
                        LoadFailedMessage(Modifier.padding(innerPadding))

                    is MainActivityUiState.Ready -> BlueCardNavDisplay(
                        isSetUp = uiState.isSetUp,
                        // Consuming the system bar insets that innerPadding already covers
                        // keeps screens' imePadding() from adding them a second time.
                        modifier = Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                    )
                }
            }
        }
    }
}
