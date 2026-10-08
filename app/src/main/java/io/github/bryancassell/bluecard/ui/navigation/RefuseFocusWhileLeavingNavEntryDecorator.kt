package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.animation.EnterExitState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/**
 * Returns a decorator that keeps input focus out of a screen while it animates out.
 *
 * NavDisplay draws the leaving screen beside the new one until its transition ends, so a key
 * such as Tab could move focus onto it. As the leaving screen is then removed, Compose gives
 * focus to the first item on the new screen that can take it (see BlueCardNavDisplay), which on
 * Badges is the search field (#285). A key that would move focus into a leaving screen does
 * nothing.
 */
@Composable
fun <T : Any> rememberRefuseFocusWhileLeavingNavEntryDecorator(): NavEntryDecorator<T> = remember {
    NavEntryDecorator(decorate = { entry -> RefuseFocusWhileLeaving { entry.Content() } })
}

@Composable
private fun RefuseFocusWhileLeaving(content: @Composable () -> Unit) {
    val leaving = LocalNavAnimatedContentScope.current.transition.targetState !=
        EnterExitState.Visible
    Box(
        Modifier
            .focusProperties { onEnter = { if (leaving) cancelFocusChange() } }
            .focusGroup()
    ) {
        content()
    }
}
