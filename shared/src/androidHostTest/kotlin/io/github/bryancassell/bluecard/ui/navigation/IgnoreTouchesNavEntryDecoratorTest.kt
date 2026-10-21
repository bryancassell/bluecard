package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs a real NavDisplay with its default transitions (a 700 ms fade). Home's button is at
 * the top and Badges' at the bottom, so a tap on one screen's button passes through the other
 * screen, which has nothing there.
 */
@RunWith(AndroidJUnit4::class)
class IgnoreTouchesNavEntryDecoratorTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val backStack = mutableStateListOf<NavKey>(Home)
    private var homeTaps = 0
    private var badgesTaps = 0
    private var doubleTapTimeout = 0L

    private fun launch(vararg keys: NavKey) {
        backStack.clear()
        backStack.addAll(keys)
        composeTestRule.setContent {
            doubleTapTimeout = LocalViewConfiguration.current.doubleTapTimeoutMillis
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberIgnoreTouchesNavEntryDecorator()
                ),
                entryProvider = entryProvider {
                    entry<Home> { Button(onClick = { homeTaps++ }) { Text("Home button") } }
                    entry<Badges> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                            Button(onClick = { badgesTaps++ }) { Text("Badges button") }
                        }
                    }
                }
            )
        }
        // Time moves only when a test moves it.
        composeTestRule.mainClock.autoAdvance = false
    }

    private fun tapHome() = composeTestRule.onNodeWithText("Home button").performClick()

    private fun tapBadges() = composeTestRule.onNodeWithText("Badges button").performClick()

    /** Changes the back stack, as a tap or Back would, and lets the transition start. */
    private fun navigate(change: MutableList<NavKey>.() -> Unit) {
        // Applying the change now, rather than when the main thread next runs, lets the next
        // frames show it.
        Snapshot.withMutableSnapshot { backStack.change() }
        composeTestRule.mainClock.advanceTimeBy(32)
    }

    @Test
    fun firstScreen_takesTouchesStraightAway() {
        launch(Home)

        tapHome()

        assertEquals(1, homeTaps)
    }

    @Test
    fun newScreen_ignoresTouchesUntilDoubleTapTimeout() {
        launch(Home)

        navigate { add(Badges) }
        // As the second tap of a quick double tap does.
        tapBadges()
        composeTestRule.mainClock.advanceTimeBy(doubleTapTimeout - 100)
        tapBadges()

        assertEquals(0, badgesTaps)
    }

    @Test
    fun newScreen_takesTouchesAfterDoubleTapTimeout_whileStillFadingIn() {
        launch(Home)

        navigate { add(Badges) }
        composeTestRule.mainClock.advanceTimeBy(400)
        // Home is still fading out.
        composeTestRule.onNodeWithText("Home button").assertExists()
        tapBadges()

        assertEquals(1, badgesTaps)
    }

    @Test
    fun leavingScreen_ignoresTouchesUntilGone() {
        launch(Home)

        navigate { add(Badges) }
        // After the timeout, where Badges has nothing to press.
        composeTestRule.mainClock.advanceTimeBy(400)
        tapHome()

        assertEquals(0, homeTaps)
    }

    @Test
    fun afterBack_closingScreenOnTopIgnoresTouchesUntilGone() {
        launch(Home, Badges)

        navigate { removeLastOrNull() }
        // Badges is drawn on top while it fades out, and covers Home.
        composeTestRule.mainClock.advanceTimeBy(400)
        tapBadges()
        tapHome()
        assertEquals(0, badgesTaps)
        assertEquals(0, homeTaps)

        composeTestRule.mainClock.advanceTimeBy(400)
        composeTestRule.onNodeWithText("Badges button").assertDoesNotExist()
        tapHome()
        assertEquals(1, homeTaps)
    }
}
