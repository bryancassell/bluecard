package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavKey

/**
 * Returns a function that opens a screen from the screen for [from]. It does nothing
 * unless that screen is still the one in use: on top of [backStack], and resumed.
 *
 * Each check stops a tap that would otherwise open a second screen. NavDisplay holds a
 * screen at STARTED while it animates in or out, and a screen that is leaving still takes
 * the taps the incoming screen doesn't. The back stack check also catches a second tap in
 * the same frame, before NavDisplay has started the first screen change.
 */
@Composable
fun rememberNavigateFrom(
    backStack: MutableList<NavKey>,
    from: NavKey,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
): (to: NavKey) -> Unit = remember(backStack, from, lifecycleOwner) {
    { to ->
        val resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        if (resumed && backStack.lastOrNull() == from) backStack.add(to)
    }
}
