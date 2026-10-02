package io.github.bryancassell.bluecard.ui.navigation

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberNavigationEventState
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs a real NavDisplay with its default transitions (a 700 ms fade). Badges handles Back
 * itself, as a page with unsaved changes does; a handler under the screens closes the others.
 */
@RunWith(AndroidJUnit4::class)
class IgnoreBackWhileLeavingNavEntryDecoratorTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val backStack = mutableStateListOf<NavKey>(Home)
    private var badgesBacks = 0
    private lateinit var dispatcher: OnBackPressedDispatcher

    private fun launch(vararg keys: NavKey) {
        backStack.clear()
        backStack.addAll(keys)
        composeTestRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            val goBack: () -> Unit = { backStack.removeLastOrNull() }
            // As in BlueCardNavDisplay: one handler, added before the screens. NavDisplay's own
            // would be added again as the screens change, ahead of a leaving screen's.
            BackHandler(enabled = backStack.size > 1, onBack = goBack)
            val entries = rememberDecoratedNavEntries(
                backStack = backStack,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberIgnoreBackWhileLeavingNavEntryDecorator()
                ),
                entryProvider = entryProvider {
                    entry<Home> { Text("Home") }
                    entry<DataManagement> { Text("Data management") }
                    entry<Badges> {
                        BackHandler { badgesBacks++ }
                        Text("Badges")
                    }
                }
            )
            val sceneState = rememberSceneState(
                entries = entries,
                sceneStrategies = listOf(SinglePaneSceneStrategy()),
                onBack = goBack
            )
            NavDisplay(
                sceneState = sceneState,
                navigationEventState = rememberNavigationEventState(sceneState)
            )
        }
        // Time moves only when a test moves it.
        composeTestRule.mainClock.autoAdvance = false
    }

    /** Changes the back stack, as a tap or Back would, and lets the transition start. */
    private fun navigate(change: MutableList<NavKey>.() -> Unit) {
        // Applying the change now, rather than when the main thread next runs, lets the next
        // frames show it.
        Snapshot.withMutableSnapshot { backStack.change() }
        composeTestRule.mainClock.advanceTimeBy(32)
    }

    private fun pressBack() = composeTestRule.runOnUiThread { dispatcher.onBackPressed() }

    @Test
    fun shownScreen_takesBack() {
        launch(Home, Badges)

        pressBack()

        assertEquals(1, badgesBacks)
        assertEquals(listOf(Home, Badges), backStack)
    }

    @Test
    fun screenOpeningAnother_ignoresBack_whileItAnimatesOut() {
        launch(Home, Badges)

        navigate { add(DataManagement) }
        composeTestRule.mainClock.advanceTimeBy(400)
        // Badges is still fading out.
        composeTestRule.onNodeWithText("Badges").assertExists()
        pressBack()

        assertEquals(0, badgesBacks)
        assertEquals(listOf(Home, Badges), backStack)
    }

    @Test
    fun screenShownAgain_takesBackAgain() {
        launch(Home, Badges)

        navigate { add(DataManagement) }
        composeTestRule.mainClock.advanceTimeBy(800)
        navigate { removeLastOrNull() }
        composeTestRule.mainClock.advanceTimeBy(800)
        pressBack()

        assertEquals(1, badgesBacks)
    }
}
