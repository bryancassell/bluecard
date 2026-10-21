package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks the slides in a real NavDisplay: laid out right-to-left, as MainActivityTest can't, since
 * the app's strings only lay out left-to-right so far, and when Back interrupts one.
 *
 * It doesn't run the accessibility checks (AccessibilityChecks): one of its frame-by-frame checks
 * fails under the native graphics they need. The screens' own tests check what they show.
 */
@RunWith(AndroidJUnit4::class)
class PageTransitionsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val backStack = mutableStateListOf<NavKey>(Home)

    private fun launch(layoutDirection: LayoutDirection) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    transitionSpec = { openPage() },
                    popTransitionSpec = { closePage() },
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
        launch(LayoutDirection.Rtl)
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
        launch(LayoutDirection.Rtl)
        val badgesAtRest = left("Badges")

        navigate { removeLastOrNull() }
        val badgesMoving = left("Badges")
        val homeMoving = left("Home")
        finishTransition()

        assertTrue(badgesMoving < badgesAtRest)
        assertTrue(homeMoving > left("Home"))
    }

    private fun unclippedLeft(text: String) =
        composeTestRule.onNodeWithText(text).getUnclippedBoundsInRoot().left

    private fun isDrawn(text: String) =
        composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun backWhileOpeningPageSlidesIn_turnsThePagesAround_sideBySide() {
        launch(LayoutDirection.Ltr)
        val width = composeTestRule.onRoot().getBoundsInRoot().width

        navigate { add(Badges) }
        var badgesLeft = unclippedLeft("Badges")
        assertTrue(badgesLeft > 0.dp)
        Snapshot.withMutableSnapshot { backStack.removeLastOrNull() }

        // NavDisplay plays the opening slide backwards. Frame by frame until Badges is gone:
        // it slides back toward the end, never further in, with Home beside it.
        repeat(60) {
            composeTestRule.mainClock.advanceTimeByFrame()
            if (!isDrawn("Badges")) return@repeat
            val badgesNow = unclippedLeft("Badges")
            assertEquals((unclippedLeft("Home") + width).value, badgesNow.value, 0.5f)
            assertTrue(badgesNow >= badgesLeft)
            badgesLeft = badgesNow
        }
        assertFalse(isDrawn("Badges"))
        assertEquals(0.dp, unclippedLeft("Home"))
    }
}
