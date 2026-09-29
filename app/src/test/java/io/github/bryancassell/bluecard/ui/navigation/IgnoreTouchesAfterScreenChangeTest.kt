package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IgnoreTouchesAfterScreenChangeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var screen by mutableStateOf<Any>(Home)
    private var taps = 0
    private var doubleTapTimeout = 0L

    @Before
    fun setUp() {
        composeTestRule.setContent {
            doubleTapTimeout = LocalViewConfiguration.current.doubleTapTimeoutMillis
            IgnoreTouchesAfterScreenChange(screen) {
                Button(onClick = { taps++ }) { Text("Tap") }
            }
        }
        // Time moves only when a test moves it.
        composeTestRule.mainClock.autoAdvance = false
    }

    private fun tap() = composeTestRule.onNodeWithText("Tap").performClick()

    /** Changes the screen, and draws one frame of it. */
    private fun changeScreen(to: Any) {
        // Applying the change now, rather than when the main thread next runs, lets the next
        // frame show it.
        Snapshot.withMutableSnapshot { screen = to }
        composeTestRule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun firstScreen_takesTouchesStraightAway() {
        tap()

        assertEquals(1, taps)
    }

    @Test
    fun afterChange_ignoresTouchesUntilDoubleTapTimeout() {
        changeScreen(Badges)

        // As the second tap of a quick double tap does.
        tap()
        composeTestRule.mainClock.advanceTimeBy(doubleTapTimeout - 50)
        tap()

        assertEquals(0, taps)
    }

    @Test
    fun afterDoubleTapTimeout_takesTouches() {
        changeScreen(Badges)

        composeTestRule.mainClock.advanceTimeBy(doubleTapTimeout)
        // The frame that redraws the screen without the cover.
        composeTestRule.mainClock.advanceTimeByFrame()
        tap()

        assertEquals(1, taps)
    }

    @Test
    fun eachChange_startsTheWaitAgain() {
        changeScreen(Badges)
        composeTestRule.mainClock.advanceTimeBy(doubleTapTimeout - 100)
        changeScreen(BadgeDetail("camping"))

        // Past the timeout for the first change, but not the second.
        composeTestRule.mainClock.advanceTimeBy(200)
        tap()
        assertEquals(0, taps)

        composeTestRule.mainClock.advanceTimeBy(doubleTapTimeout)
        tap()
        assertEquals(1, taps)
    }
}
