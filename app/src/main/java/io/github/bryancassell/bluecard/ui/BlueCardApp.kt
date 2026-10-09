package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
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
            val insets = ScaffoldDefaults.contentWindowInsets
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = { StatusBarBackground(insets) },
                contentWindowInsets = insets
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
 * the page's own color, its cut-off text ran into the clock (#315). It shows all the time:
 * showing it only once a page scrolls would need every page to say how far it's scrolled. As the
 * Scaffold's top bar, its height is where every page starts, so it's as tall as the top of the
 * Scaffold's [insets], which pages would start below without it: the status bar, or a window's
 * caption bar or a camera cutout where either is taller.
 */
@Composable
private fun StatusBarBackground(insets: WindowInsets) {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(insets)
            .background(MaterialTheme.colorScheme.surfaceContainer)
    )
}
