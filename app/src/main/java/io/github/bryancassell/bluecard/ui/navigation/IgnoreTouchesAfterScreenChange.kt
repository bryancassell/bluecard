package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlinx.coroutines.delay

/**
 * Shows [content], and ignores touches on it from when [screen] changes until the new screen
 * has been shown for the double-tap timeout. The first screen takes touches straight away.
 *
 * The incoming screen is drawn on top while it fades in, so without this the second tap of a
 * double tap that opened it would press whatever is under the finger. Taps after the timeout
 * work, even while the screen is still fading in.
 */
@Composable
fun IgnoreTouchesAfterScreenChange(
    screen: Any,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val timeoutMillis = LocalViewConfiguration.current.doubleTapTimeoutMillis
    // The last screen shown for the whole timeout. Comparing with it here, rather than in an
    // effect, covers a new screen from its first frame. Going back to it within the timeout,
    // which takes Back rather than a tap, ends the wait.
    var settledScreen by remember { mutableStateOf(screen) }
    LaunchedEffect(screen, timeoutMillis) {
        delay(timeoutMillis)
        settledScreen = screen
    }
    // Fills the space, not just the size of the screen shown, so the cover below reaches
    // every screen.
    Box(modifier.fillMaxSize()) {
        content()
        if (screen != settledScreen) {
            // A cover that takes touches and does nothing with them. Hit testing stops at the
            // topmost layout that takes touches, so the screens under the cover never see them.
            Box(Modifier.matchParentSize().pointerInput(Unit) {})
        }
    }
}
