package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.animation.EnterExitState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner

/**
 * Returns a decorator that turns off a screen's back handlers while it animates out, so Back
 * goes to the screen shown in its place.
 *
 * NavDisplay keeps a leaving screen composed until its transition ends, back handlers and all,
 * and the most recently added handler takes Back. A screen that has left the back stack is
 * already stopped, which turns its handlers off, but one that opened another is still resumed.
 * Without this, a page with unsaved changes would ask to discard them on a Back just after it
 * opened another page, while it slides away, rather than the new page closing.
 */
@Composable
fun <T : Any> rememberIgnoreBackWhileLeavingNavEntryDecorator(): NavEntryDecorator<T> = remember {
    NavEntryDecorator(decorate = { entry -> IgnoreBackWhileLeaving { entry.Content() } })
}

@Composable
private fun IgnoreBackWhileLeaving(content: @Composable () -> Unit) {
    val transition = LocalNavAnimatedContentScope.current.transition
    val leaving = transition.targetState != EnterExitState.Visible
    // The screen's handlers are added to a dispatcher of its own, which turns them all off.
    val owner = rememberNavigationEventDispatcherOwner(enabled = !leaving)
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner, content = content)
}
