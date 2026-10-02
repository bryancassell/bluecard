package io.github.bryancassell.bluecard.ui.navigation

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberNavigationEventState
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.ui.ConfirmDiscardOnBack
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs a real NavDisplay, deciding Back as BlueCardNavDisplay does. Badges has unsaved
 * changes while [badgesChanged].
 */
@RunWith(AndroidJUnit4::class)
class UnsavedChangesByPageTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val backStack = mutableStateListOf<NavKey>(Home)
    private var badgesChanged by mutableStateOf(true)
    private lateinit var dispatcher: OnBackPressedDispatcher

    private fun launch(vararg keys: NavKey) {
        backStack.clear()
        backStack.addAll(keys)
        composeTestRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            val unsavedChanges = remember { UnsavedChangesByPage() }
            val entries = entryProvider {
                entry<Home> { Text("Home") }
                entry<DataManagement> { Text("Data management") }
                entry<Badges> {
                    ConfirmDiscardOnBack(badgesChanged, onDiscard = { backStack.remove(Badges) })
                    Text("Badges")
                }
            }
            val goBack: () -> Unit = {
                if (backStack.size > 1 && !unsavedChanges.askToDiscard(entries(backStack.last()))) {
                    backStack.removeLastOrNull()
                }
            }
            // Always on, unlike the app's, so a Back before the next frame reaches it after a
            // page opens from Home.
            BackHandler(onBack = goBack)
            val sceneState = rememberSceneState(
                entries = rememberDecoratedNavEntries(
                    backStack = backStack,
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        unsavedChanges.decorator
                    ),
                    entryProvider = entries
                ),
                sceneStrategies = listOf(SinglePaneSceneStrategy()),
                onBack = goBack
            )
            NavDisplay(
                sceneState = sceneState,
                navigationEventState = rememberNavigationEventState(sceneState)
            )
        }
    }

    /** Presses Back [times] times, one right after another. */
    private fun pressBack(times: Int = 1) = composeTestRule.runOnUiThread {
        repeat(times) { dispatcher.onBackPressed() }
    }

    /** Runs [changes] before the next frame, then lets every transition finish. */
    private fun beforeNextFrame(changes: () -> Unit) {
        composeTestRule.mainClock.autoAdvance = false
        changes()
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
    }

    private fun dialogTitle() = composeTestRule.onNodeWithText("Discard changes?")

    @Test
    fun back_onAPageWithChanges_asks() {
        launch(Home, Badges)

        pressBack()

        assertEquals(listOf(Home, Badges), backStack)
        dialogTitle().assertIsDisplayed()
    }

    @Test
    fun back_rightAfterAPageWithChangesOpensAnother_closesTheNewPage() {
        launch(Home, Badges)

        beforeNextFrame {
            Snapshot.withMutableSnapshot { backStack.add(DataManagement) }
            pressBack()
        }

        assertEquals(listOf(Home, Badges), backStack)
        dialogTitle().assertDoesNotExist()
    }

    @Test
    fun coveredPage_keepsItsChanges_forBackBeforeItsShownAgain() {
        launch(Home, Badges)
        backStack.add(DataManagement)
        composeTestRule.onNodeWithText("Badges").assertDoesNotExist()

        // The second Back arrives before Badges is shown again.
        beforeNextFrame { pressBack(times = 2) }

        assertEquals(listOf(Home, Badges), backStack)
        dialogTitle().assertIsDisplayed()
    }

    @Test
    fun poppedPage_forgetsItsChanges() {
        launch(Home, Badges)
        pressBack()
        composeTestRule.onNodeWithText("Discard").performClick()
        composeTestRule.onNodeWithText("Badges").assertDoesNotExist()

        // Opened again without changes, Back closes it before a frame shows it.
        badgesChanged = false
        beforeNextFrame {
            Snapshot.withMutableSnapshot { backStack.add(Badges) }
            pressBack()
        }

        assertEquals(listOf(Home), backStack)
        dialogTitle().assertDoesNotExist()
    }
}
