package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filterIsInstance

/**
 * Keeps all of [content] in view while something in it has focus, when it fits in the page's
 * [scrollState] viewport: as it takes focus, once the keyboard has opened, which shrinks the
 * viewport, and as a field in it grows. Otherwise the page keeps only a focused field's cursor in
 * view, as it does for the fields above. A page puts its last field and Save button in it, so
 * Save stays above the keyboard while the scout types there (#172, #178). The line being typed
 * matters more than Save, which is a scroll away. Scrolling keeps the page laid out as before; a
 * bar fixed above the keyboard would have taken room from the fields while typing.
 */
// imeAnimationTarget is experimental, and the only way to tell the keyboard is still moving. A
// change to it would fail the build when Compose is updated.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeepInViewWhileFocused(scrollState: ScrollState, content: @Composable ColumnScope.() -> Unit) {
    val requester = remember { BringIntoViewRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    var height by remember { mutableIntStateOf(0) }
    // Read here, they recompose only this function on each frame as the keyboard moves.
    val viewportHeight = scrollState.viewportSize
    val density = LocalDensity.current
    val keyboardMoving =
        WindowInsets.ime.getBottom(density) != WindowInsets.imeAnimationTarget.getBottom(density)
    // The viewport's height when the keyboard last stopped, and whether a request is owed: focus
    // came in, or the last request is still running or was cut short by a change here, such as
    // the keyboard starting to move. Not read in composition, so changing them doesn't recompose.
    var settledViewportHeight by remember { mutableIntStateOf(viewportHeight) }
    var requestOwed by remember { mutableStateOf(false) }
    // The scout's drag counts as scrolling away, even while the keyboard is still moving and no
    // request runs. Every start is seen, even of a drag that ends before the next frame.
    val dragStarts = remember(scrollState) {
        scrollState.interactionSource.interactions.filterIsInstance<DragInteraction.Start>()
    }
    LaunchedEffect(dragStarts) { dragStarts.collect { requestOwed = false } }
    // Asks again each time one changes, which cancels the request before.
    LaunchedEffect(hasFocus, height, viewportHeight, keyboardMoving) {
        // While a request runs, the page stops following the cursor as the keyboard shrinks the
        // viewport. Once they no longer fit, the cursor of a tall field could be left behind the
        // keyboard.
        if (keyboardMoving) return@LaunchedEffect
        // A viewport that grew, as when the keyboard closes, can't have hidden them. Asking then
        // would pull the page back to them after the scout has scrolled away. An owed request is
        // still asked for: a keyboard that got shorter before it finished, as a number pad can,
        // would otherwise leave Save behind the keyboard (#244, #253).
        val viewportGrew = viewportHeight > settledViewportHeight
        settledViewportHeight = viewportHeight
        requestOwed = hasFocus && height <= viewportHeight && (!viewportGrew || requestOwed)
        if (requestOwed) {
            // Returns once the request is done, and also when another scroll stops it, such as
            // the scout's drag or a screen reader's scroll. It throws only when this effect is
            // cancelled.
            requester.bringIntoView()
            requestOwed = false
        }
    }
    Column(
        modifier = Modifier
            .bringIntoViewRequester(requester)
            .onFocusChanged {
                // Owed even if the keyboard is moving, so the effect asks once it stops.
                if (it.hasFocus && !hasFocus) requestOwed = true
                hasFocus = it.hasFocus
            }
            .onSizeChanged { height = it.height },
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}
