package io.github.bryancassell.bluecard.testing

import android.graphics.Insets
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue

/** A small phone's screen, less its system bars, where the keyboard leaves little of a page. */
const val SMALL_PHONE = "w360dp-h560dp"

/**
 * The on-screen keyboard for a page under test, which Robolectric doesn't show. [Content] finds
 * the page's [view], and [open], [close] and [move] move the keyboard's top edge over it, as a
 * phone does. Tests that measure the page use native graphics.
 */
class OnScreenKeyboard(private val rule: ComposeContentTestRule) {
    /** The page's view, which the keyboard's insets are sent to. */
    lateinit var view: View
        private set

    @Composable
    fun Content(content: @Composable () -> Unit) {
        view = LocalView.current
        content()
    }

    /**
     * Opens the keyboard over the bottom of the page, as the system does once a field has focus:
     * after the page has handled the focus change.
     */
    fun open() {
        rule.waitForIdle()
        move(from = 0.dp, to = HEIGHT)
    }

    /** Closes the keyboard, as the scout does with Back while a field keeps focus. */
    fun close() {
        rule.waitForIdle()
        move(from = HEIGHT, to = 0.dp)
    }

    /**
     * Moves the keyboard's top edge as the system does: it sends the page its final insets, then
     * the insets of each frame of the animation. It starts at once, even while the page is still
     * scrolling, and runs [midway] halfway through, as the scout can act while it moves.
     */
    fun move(from: Dp, to: Dp, midway: () -> Unit = {}) {
        val (start, end) = with(rule.density) { from.roundToPx() to to.roundToPx() }
        val animation = WindowInsetsAnimation(WindowInsets.Type.ime(), null, 250)
        val bounds = WindowInsetsAnimation.Bounds(
            Insets.NONE,
            Insets.of(0, 0, 0, maxOf(start, end))
        )
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread {
            view.dispatchWindowInsetsAnimationPrepare(animation)
            view.dispatchApplyWindowInsets(insets(end))
            view.dispatchWindowInsetsAnimationStart(animation, bounds)
        }
        for (frame in 1..FRAMES) {
            rule.mainClock.advanceTimeByFrame()
            rule.runOnUiThread {
                animation.fraction = frame.toFloat() / FRAMES
                val height = start + (end - start) * frame / FRAMES
                view.dispatchWindowInsetsAnimationProgress(insets(height), listOf(animation))
            }
            if (frame == FRAMES / 2) midway()
        }
        rule.runOnUiThread { view.dispatchWindowInsetsAnimationEnd(animation) }
        rule.mainClock.autoAdvance = true
    }

    private fun insets(height: Int): WindowInsets = WindowInsets.Builder()
        .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, height))
        .setVisible(WindowInsets.Type.ime(), height > 0)
        .build()

    /**
     * Checks that [bounds] are on the page above a keyboard [keyboardHeight] tall. Unclipped
     * bounds are needed for this: the page clips what's behind the keyboard.
     */
    fun assertAbove(bounds: DpRect, keyboardHeight: Dp = HEIGHT) {
        val keyboardTop = rule.onRoot().getUnclippedBoundsInRoot().bottom - keyboardHeight
        assertTrue(
            "$bounds isn't between the top of the page and the keyboard at $keyboardTop",
            bounds.top >= 0.dp && bounds.bottom <= keyboardTop
        )
    }

    companion object {
        /** About as tall as a phone's keyboard. */
        val HEIGHT = 300.dp

        /** About how many frames a keyboard takes to open or close. */
        private const val FRAMES = 15
    }
}
