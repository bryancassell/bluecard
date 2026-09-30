package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs a real NavDisplay with its default transitions (a 700 ms fade). */
@RunWith(AndroidJUnit4::class)
class DrawnScreensTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val backStack = mutableStateListOf<NavKey>(Home, Badges)
    private val drawnScreens = DrawnScreens()
    private lateinit var entries: (NavKey) -> NavEntry<NavKey>

    private fun launch() {
        composeTestRule.setContent {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryDecorators = listOf(drawnScreens.decorator),
                entryProvider = entryProvider {
                    entry<Home> { Text("Home") }
                    entry<Badges> { Text("Badges") }
                }.also { entries = it }
            )
        }
        // Time moves only when a test moves it.
        composeTestRule.mainClock.autoAdvance = false
    }

    private fun isDrawn(key: NavKey) = entries(key) in drawnScreens

    @Test
    fun screenOnTop_isDrawn_andTheOneUnderItIsnt() {
        launch()

        assertTrue(isDrawn(Badges))
        assertFalse(isDrawn(Home))
    }

    @Test
    fun screenAnimatingOut_isDrawnUntilItsGone() {
        launch()

        // As Back does.
        Snapshot.withMutableSnapshot { backStack.removeLastOrNull() }
        composeTestRule.mainClock.advanceTimeBy(32)
        assertTrue(isDrawn(Badges))
        assertTrue(isDrawn(Home))

        composeTestRule.mainClock.advanceTimeBy(1_000)
        assertFalse(isDrawn(Badges))
        assertTrue(isDrawn(Home))
    }
}
