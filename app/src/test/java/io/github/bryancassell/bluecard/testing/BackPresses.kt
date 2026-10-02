package io.github.bryancassell.bluecard.testing

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/**
 * Back for a screen under test, as the app gives it. [Content] puts a handler under the screen,
 * as the navigation root's, which counts each Back the screen doesn't take ([closes]), and
 * [press] presses Back.
 */
class BackPresses {
    /** How many times Back reached the handler under the screen, which would close the page. */
    var closes = 0
        private set

    private lateinit var dispatcher: OnBackPressedDispatcher

    @Composable
    fun Content(content: @Composable () -> Unit) {
        dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
        BackHandler { closes++ }
        content()
    }

    fun press(rule: ComposeContentTestRule) {
        rule.runOnUiThread { dispatcher.onBackPressed() }
    }
}
