package io.github.bryancassell.bluecard.testing

import android.view.ViewConfiguration
import androidx.compose.ui.test.junit4.ComposeTestRule

/**
 * Moves the clock past the double-tap timeout, through which a dialog ignores taps as it opens
 * (`ConfirmDialog`, and the merge's dialog), as a person's next tap would come. Compose's test
 * rules don't skip that wait, so a test that taps a dialog it has just opened needs this first.
 *
 * It waits for idle first. A step that opens the dialog from outside Compose's input, such as a
 * Back press sent with runOnUiThread, hasn't composed it yet, and the dialog's timer starts only
 * when it composes: advancing right away would end a frame short of the timeout. A test with the
 * clock frozen, where waiting for idle draws nothing, should let the dialog draw before calling
 * this.
 */
fun ComposeTestRule.waitPastDoubleTapTimeout() {
    waitForIdle()
    mainClock.advanceTimeBy(ViewConfiguration.getDoubleTapTimeout().toLong())
}
