package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation3.runtime.NavKey

/**
 * Returns a function that opens a screen from the screen for [from]. It does nothing unless
 * that screen is on top of [shownBackStack], the screens NavDisplay is showing, which it
 * reads at the time of the call. It also does nothing if the screen it would open is still
 * drawn ([isDrawn]), animating out, since it would come back as it was ([DrawnScreens]).
 *
 * A screen ignores touches while it animates out ([rememberIgnoreTouchesNavEntryDecorator]),
 * but a second tap before its animation starts, such as in the same frame as the first, or a
 * screen reader's click can still reach it. It's no longer on top by then, so that doesn't
 * open another screen. After Back, the closing screen's cover takes touches for the screen
 * under it too, but a screen reader's click on that screen can still open the one closing.
 */
@Composable
fun rememberNavigateFrom(
    backStack: MutableList<NavKey>,
    from: NavKey,
    isDrawn: (NavKey) -> Boolean,
    shownBackStack: () -> List<NavKey>
): (to: NavKey) -> Unit {
    val currentShownBackStack by rememberUpdatedState(shownBackStack)
    val currentIsDrawn by rememberUpdatedState(isDrawn)
    return remember(backStack, from) {
        { to ->
            if (currentShownBackStack().lastOrNull() == from && !currentIsDrawn(to)) {
                backStack.add(to)
            }
        }
    }
}
