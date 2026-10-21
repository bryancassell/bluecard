package io.github.bryancassell.bluecard.testing

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import io.github.bryancassell.bluecard.ui.LocalUnsavedChanges
import io.github.bryancassell.bluecard.ui.UnsavedChanges

/**
 * Back for a screen under test, as the navigation root gives it. [Content] provides the
 * screen's [unsavedChanges] and puts a handler under the screen that asks to discard them,
 * if it has any, or else counts a Back that would close the page ([closes]). [press] presses
 * Back.
 */
class BackPresses {
    val unsavedChanges = UnsavedChanges()

    /** How many times Back would have closed the page. */
    var closes = 0
        private set

    private lateinit var dispatcher: OnBackPressedDispatcher

    @Composable
    fun Content(content: @Composable () -> Unit) {
        dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
        BackHandler { if (!unsavedChanges.askToDiscard()) closes++ }
        CompositionLocalProvider(LocalUnsavedChanges provides unsavedChanges, content = content)
    }

    fun press(rule: ComposeContentTestRule) {
        rule.runOnUiThread { dispatcher.onBackPressed() }
    }
}
