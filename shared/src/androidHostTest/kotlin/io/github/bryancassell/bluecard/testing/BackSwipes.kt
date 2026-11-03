package io.github.bryancassell.bluecard.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

/**
 * Back swipes from the left edge, reported with their progress as iOS's edge swipe reports them:
 * the finger's travel over the screen's width. [Connect] connects them to the back handlers of
 * the content composed with it.
 */
class BackSwipes(private val rule: ComposeContentTestRule) {
    private val input = DirectNavigationEventInput()

    @Composable
    fun Connect() {
        val dispatcher = LocalNavigationEventDispatcherOwner.current!!.navigationEventDispatcher
        DisposableEffect(dispatcher) {
            dispatcher.addInput(input)
            onDispose { dispatcher.removeInput(input) }
        }
    }

    /** Starts a swipe and moves the finger [progress] of the way across. */
    fun start(progress: Float) = rule.runOnUiThread {
        input.backStarted(event(0f))
        input.backProgressed(event(progress))
    }

    /** Lets go far or fast enough to go back. */
    fun complete() = rule.runOnUiThread { input.backCompleted() }

    /** Lets go short of going back, or moves the finger back to the edge. */
    fun cancel() = rule.runOnUiThread { input.backCancelled() }

    private fun event(progress: Float) =
        NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = progress)
}
