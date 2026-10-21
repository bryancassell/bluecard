package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs a real NavDisplay with its default transitions (a 700 ms fade). Robolectric shows it out
 * of touch mode, where buttons can take focus.
 */
@RunWith(AndroidJUnit4::class)
class RefuseFocusWhileLeavingNavEntryDecoratorTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val backStack = mutableStateListOf<NavKey>(Home)

    private fun launch(vararg keys: NavKey) {
        backStack.clear()
        backStack.addAll(keys)
        composeTestRule.setContent {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberRefuseFocusWhileLeavingNavEntryDecorator()
                ),
                entryProvider = entryProvider {
                    entry<Home> { Button(onClick = {}) { Text("Home button") } }
                    entry<Badges> { Button(onClick = {}) { Text("Badges button") } }
                }
            )
        }
        // Time moves only when a test moves it.
        composeTestRule.mainClock.autoAdvance = false
    }

    private fun homeButton() = composeTestRule.onNodeWithText("Home button")

    private fun badgesButton() = composeTestRule.onNodeWithText("Badges button")

    /** Changes the back stack, as a tap or Back would, and lets the transition start. */
    private fun navigate(change: MutableList<NavKey>.() -> Unit) {
        Snapshot.withMutableSnapshot { backStack.change() }
        composeTestRule.mainClock.advanceTimeBy(100)
    }

    @Test
    fun shownScreen_takesFocus() {
        launch(Home)

        homeButton().requestFocus()

        homeButton().assertIsFocused()
    }

    @Test
    fun leavingScreen_refusesFocus() {
        launch(Home)
        navigate { add(Badges) }

        homeButton().requestFocus()

        homeButton().assertIsNotFocused()
    }

    @Test
    fun arrivingScreen_takesFocusWhileTheLeavingOneIsDrawn() {
        launch(Home)
        navigate { add(Badges) }

        badgesButton().requestFocus()

        badgesButton().assertIsFocused()
    }

    @Test
    fun afterBack_closingScreenRefusesFocus_andTheOneUnderItTakesIt() {
        launch(Home, Badges)
        navigate { removeLastOrNull() }

        badgesButton().requestFocus()
        badgesButton().assertIsNotFocused()
        homeButton().requestFocus()

        homeButton().assertIsFocused()
    }
}
