package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
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
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = { StatusBarBackground() }
            ) { innerPadding ->
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

/**
 * Fills the space behind the status bar in a color set apart from the page's: Material's color for
 * a top app bar that a page has scrolled under. A page scrolled up to the bar ends at its edge; on
 * the page's own color, its cut-off text ran into the clock. As the Scaffold's top bar, it sets
 * where pages start, so it covers all the system bars at the top, as the Scaffold's padding did
 * without it: a window's caption bar too, in desktop windowing.
 */
@Composable
private fun StatusBarBackground() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.systemBars)
            .background(MaterialTheme.colorScheme.surfaceContainer)
    )
}
