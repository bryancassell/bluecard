package io.github.bryancassell.bluecard.ui.navigation

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Checks the easing, how far into a back swipe the page fades, and the slides in a real
 * NavDisplay laid out right-to-left. MainActivityTest checks the slides left-to-right, the only
 * direction the app's strings lay out in so far.
 */
// Draws for real, so a test can read which page is on top.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class PageTransitionsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val backStack = mutableStateListOf<NavKey>(Home)
    private lateinit var activity: ComponentActivity

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

    private fun launch(layoutDirection: LayoutDirection = LayoutDirection.Ltr) {
        composeTestRule.setContent {
            activity = LocalActivity.current as ComponentActivity
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                val density = LocalDensity.current
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    transitionSpec = { openPage(density, layoutDirection) },
                    popTransitionSpec = { closePage(density, layoutDirection) },
                    predictivePopTransitionSpec = { swipeBackPage(density, layoutDirection, it) },
                    entryProvider = entryProvider {
                        entry<Home> { Page("Home", Color.Blue) }
                        entry<Badges> { Page("Badges", Color.Red) }
                    }
                )
            }
        }
    }

    /** Swipes back from [edge] and holds at [progress]. */
    private fun swipeBack(progress: Float, edge: Int = NavigationEvent.EDGE_LEFT) {
        val gesture = DirectNavigationEventInput()
        composeTestRule.runOnUiThread {
            activity.navigationEventDispatcher.addInput(gesture)
            gesture.backStarted(NavigationEvent(edge, progress = 0f))
            gesture.backProgressed(NavigationEvent(edge, progress))
        }
    }

    /** The color three quarters of the way across, where the swiped page covers. */
    private fun colorOnTop(): Color {
        val pixels = composeTestRule.onRoot().captureToImage().toPixelMap()
        return pixels[pixels.width * 3 / 4, pixels.height / 2]
    }

    @Test
    fun swipingBack_keepsThePageOpaqueUntilHalfway() {
        backStack.add(Badges)
        launch()

        swipeBack(progress = 0.45f)

        assertEquals(Color.Red, colorOnTop())
    }

    @Test
    fun swipingBack_fadesThePageOutPastHalfway() {
        backStack.add(Badges)
        launch()

        swipeBack(progress = 0.75f)

        assertEquals(Color.Blue, colorOnTop())
    }

    private fun launchRightToLeft() {
        launch(LayoutDirection.Rtl)
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

    // The finger moves right, so a page sliding left would move against it.
    @Test
    fun rightToLeft_swipingBackFromTheLeftEdge_leavesThePageInPlace() {
        backStack.add(Badges)
        launch(LayoutDirection.Rtl)
        val badgesAtRest = left("Badges")

        swipeBack(progress = 0.4f, edge = NavigationEvent.EDGE_LEFT)

        assertEquals(badgesAtRest, left("Badges"))
    }
}

/** A page filled with [color], so the pixels show which page is on top. */
@Composable
private fun Page(name: String, color: Color) {
    Box(Modifier.fillMaxSize().background(color)) { Text(name) }
}
