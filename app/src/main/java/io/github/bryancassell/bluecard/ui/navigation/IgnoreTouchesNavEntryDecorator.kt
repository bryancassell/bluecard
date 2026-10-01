package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.animation.EnterExitState
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
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import kotlinx.coroutines.delay

/**
 * Returns a decorator that ignores touches on a screen while it animates out, and for the
 * double-tap timeout after it starts animating in. The first screen takes touches straight
 * away.
 *
 * NavDisplay draws both screens during a transition: side by side as pages slide, or one over
 * the other as they crossfade. Without this, the second tap of a double tap that opened a
 * screen would press whatever is under the finger on it, and a tap could reach a leaving
 * screen's controls wherever they're still drawn.
 */
@Composable
fun <T : Any> rememberIgnoreTouchesNavEntryDecorator(): NavEntryDecorator<T> = remember {
    NavEntryDecorator(decorate = { entry -> IgnoreTouchesDuringTransition { entry.Content() } })
}

@Composable
private fun IgnoreTouchesDuringTransition(content: @Composable () -> Unit) {
    val transition = LocalNavAnimatedContentScope.current.transition
    val leaving = transition.targetState != EnterExitState.Visible
    // A screen shown without a transition, such as the first one, isn't arriving.
    var arriving by remember { mutableStateOf(transition.currentState != transition.targetState) }
    val timeoutMillis = LocalViewConfiguration.current.doubleTapTimeoutMillis
    if (arriving) {
        LaunchedEffect(timeoutMillis) {
            delay(timeoutMillis)
            arriving = false
        }
    }
    // Fills the space, not just the screen's content, so the cover below takes touches
    // anywhere a screen under it could.
    Box(Modifier.fillMaxSize()) {
        content()
        if (leaving || arriving) {
            // A cover that takes touches and does nothing with them. Hit testing stops at the
            // topmost layout that takes touches, so nothing under the cover sees them.
            Box(Modifier.matchParentSize().pointerInput(Unit) {})
        }
    }
}
