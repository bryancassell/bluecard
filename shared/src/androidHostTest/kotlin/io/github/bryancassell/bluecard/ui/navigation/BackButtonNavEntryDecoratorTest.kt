package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.testing.AccessibilityChecks
import io.github.bryancassell.bluecard.ui.theme.BlueCardTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * The Back button iOS's pages get, in a real NavDisplay. It's the one control the navigation root
 * draws, so its accessibility checks run here. BlueCardNavDisplayIosBackTest checks what the
 * button and the swipe do in the app.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackButtonNavEntryDecoratorTest {
    @get:Rule(order = 0)
    val composeTestRule = createComposeRule()

    @get:Rule(order = 1)
    val accessibilityChecks = AccessibilityChecks(composeTestRule)

    private val backStack = mutableStateListOf<NavKey>(Home)

    private val entries = entryProvider<NavKey> {
        entry<Home>(metadata = WithoutBackButton) { Text("Home") }
        entry<Badges> { Text("Badges") }
    }

    /** The content keys of the pages whose Back button was pressed. */
    private val pressed = mutableListOf<Any>()

    private fun launch(vararg keys: NavKey) {
        backStack.clear()
        backStack.addAll(keys)
        composeTestRule.setContent {
            BlueCardTheme {
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryDecorators = listOf(rememberBackButtonNavEntryDecorator { pressed += it }),
                    entryProvider = entries
                )
            }
        }
    }

    private fun backButton() =
        composeTestRule.onNode(hasContentDescription("Back") and hasClickAction())

    @Test
    fun page_hasABackButton_readAsBack_withA48dpTouchTarget() {
        launch(Home, Badges)

        // Drawn at 40dp, it takes touches over 48dp, more than the 44pt iOS asks for.
        backButton().assertIsDisplayed()
            .assertTouchWidthIsEqualTo(48.dp)
            .assertTouchHeightIsEqualTo(48.dp)
    }

    @Test
    fun pageWithoutBackButton_hasNone() {
        launch(Home)

        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        backButton().assertDoesNotExist()
    }

    @Test
    fun page_startsBelowTheBar() {
        launch(Home, Badges)

        val barBottom = backButton().getBoundsInRoot().bottom
        assertTrue(composeTestRule.onNodeWithText("Badges").getBoundsInRoot().top >= barBottom)
    }

    @Test
    fun backButton_reportsItsPage() {
        launch(Home, Badges)

        backButton().performClick()

        assertEquals(listOf(entries(Badges).contentKey), pressed)
    }
}
