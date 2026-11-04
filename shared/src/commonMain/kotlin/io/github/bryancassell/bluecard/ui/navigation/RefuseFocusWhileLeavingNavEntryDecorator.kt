package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.animation.EnterExitState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/**
 * Returns a decorator that keeps input focus out of a screen while it animates out.
 *
 * NavDisplay draws the leaving screen beside the new one until its transition ends, so a key
 * such as Tab could move focus onto it. As the leaving screen is then removed, its focus would go
 * to the focus target around the screens (see BlueCardNavDisplay), not into the new screen. A key
 * that would move focus into a leaving screen does nothing.
 *
 * Every screen refuses focus while [isSwiping], as a back swipe that moves the pages, as on iOS,
 * holds the screen under the top one on view, though it isn't on top.
 */
@Composable
fun <T : Any> rememberRefuseFocusWhileLeavingNavEntryDecorator(
    isSwiping: () -> Boolean = { false }
): NavEntryDecorator<T> {
    val currentIsSwiping by rememberUpdatedState(isSwiping)
    return remember {
        NavEntryDecorator(decorate = { entry ->
            RefuseFocusWhileLeaving(isSwiping = { currentIsSwiping() }) { entry.Content() }
        })
    }
}

@Composable
private fun RefuseFocusWhileLeaving(isSwiping: () -> Boolean, content: @Composable () -> Unit) {
    val leaving = LocalNavAnimatedContentScope.current.transition.targetState !=
        EnterExitState.Visible
    Box(
        Modifier
            .focusProperties { onEnter = { if (leaving || isSwiping()) cancelFocusChange() } }
            .focusGroup()
    ) {
        content()
    }
}
