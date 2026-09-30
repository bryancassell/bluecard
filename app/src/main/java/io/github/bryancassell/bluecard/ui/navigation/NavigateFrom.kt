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
 * A screen ignores touches while it animates out ([rememberIgnoreTouchesNavEntryDecorator]),
 * but a second tap before its animation starts, such as in the same frame as the first, or a
 * screen reader's click can still reach it. It's no longer on top by then, so that doesn't
 * open another screen.
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

/**
 * Closes the screen for [key] if it's on top of this back stack, as when the screen is done.
 * Does nothing otherwise, such as when the scout has already gone back from it.
 */
fun MutableList<NavKey>.closeIfOnTop(key: NavKey) {
    if (lastOrNull() == key) removeAt(lastIndex)
}
