package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation3.runtime.NavKey

/**
 * Returns a function that opens a screen from the screen for [from]. It does nothing unless
 * that screen is on top of [shownBackStack], the screens NavDisplay is showing, which it
 * reads at the time of the call.
 *
 * A screen that is animating out still takes the taps the incoming screen doesn't, but it's
 * no longer on top, so a second tap, whether in the same frame or during the animation,
 * doesn't open another screen. Taps on the incoming screen work straight away.
 */
@Composable
fun rememberNavigateFrom(
    backStack: MutableList<NavKey>,
    from: NavKey,
    shownBackStack: () -> List<NavKey>
): (to: NavKey) -> Unit {
    val currentShownBackStack by rememberUpdatedState(shownBackStack)
    return remember(backStack, from) {
        { to -> if (currentShownBackStack().lastOrNull() == from) backStack.add(to) }
    }
}
