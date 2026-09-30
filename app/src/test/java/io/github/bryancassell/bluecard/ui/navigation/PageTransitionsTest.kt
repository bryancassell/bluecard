package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks the easing, and the slides in a real NavDisplay laid out right-to-left. MainActivityTest
 * checks them left-to-right, the only direction the app's strings lay out in so far.
 */
@RunWith(AndroidJUnit4::class)
class PageTransitionsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val backStack = mutableStateListOf<NavKey>(Home)

    @Test
    fun easing_followsThePlatformPath() {
        // Points on the path in PageTransitions.kt, found by solving its two curves directly.
        val points = mapOf(
            0f to 0f,
            0.05f to 0.02061f,
            0.1f to 0.09348f,
            0.166666f to 0.4f,
            0.25f to 0.77283f,
            0.5f to 0.95061f,
            0.75f to 0.9914f,
            1f to 1f
        )
        for ((x, y) in points) {
            assertEquals("at $x", y, FastOutExtraSlowInEasing.transform(x), 0.001f)
        }
    }

    private fun launchRightToLeft() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                val density = LocalDensity.current
                val layoutDirection = LocalLayoutDirection.current
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    transitionSpec = { openPage(density, layoutDirection) },
                    popTransitionSpec = { closePage(density, layoutDirection) },
                    entryProvider = entryProvider {
                        entry<Home> { Text("Home") }
                        entry<Badges> { Text("Badges") }
                    }
                )
            }
        }
        // Time moves only when a test moves it.
        composeTestRule.mainClock.autoAdvance = false
    }

    /** Changes the back stack, as a tap or Back would, and plays the transition for 100 ms. */
    private fun navigate(change: MutableList<NavKey>.() -> Unit) {
        Snapshot.withMutableSnapshot { backStack.change() }
        composeTestRule.mainClock.advanceTimeBy(100)
    }

    private fun finishTransition() = composeTestRule.mainClock.advanceTimeBy(1_000)

    private fun left(text: String) = composeTestRule.onNodeWithText(text).getBoundsInRoot().left

    @Test
    fun rightToLeft_openingPage_slidesItInFromTheLeft_andThePageLeftSlidesRight() {
        launchRightToLeft()
        val homeAtRest = left("Home")

        navigate { add(Badges) }
        val homeMoving = left("Home")
        val badgesMoving = left("Badges")
        finishTransition()

        assertTrue(badgesMoving < left("Badges"))
        assertTrue(homeMoving > homeAtRest)
    }

    @Test
    fun rightToLeft_back_slidesThePageLeft_andThePageReturnedToSlidesInFromTheRight() {
        backStack.add(Badges)
        launchRightToLeft()
        val badgesAtRest = left("Badges")

        navigate { removeLastOrNull() }
        val badgesMoving = left("Badges")
        val homeMoving = left("Home")
        finishTransition()

        assertTrue(badgesMoving < badgesAtRest)
        assertTrue(homeMoving > left("Home"))
    }
}
