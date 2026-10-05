package io.github.bryancassell.bluecard.ui

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.MainActivityUiState
import io.github.bryancassell.bluecard.testing.isPaneTitledWithItsText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Loading and LoadFailed states. MainActivityTest covers Ready, which needs Hilt for the
 * screens.
 */
@RunWith(AndroidJUnit4::class)
class BlueCardAppTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loading_showsNeitherOnboardingNorHome() {
        composeTestRule.setContent {
            BlueCardApp(MainActivityUiState.Loading, onDismissDamagedProgressNotice = {})
        }

        composeTestRule.onNodeWithText("Welcome to BlueCard").assertDoesNotExist()
        // Home's button that opens the badge list, and its loading indicator.
        composeTestRule.onNodeWithText("Merit badges").assertDoesNotExist()
        composeTestRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo.Indeterminate
            )
        ).assertDoesNotExist()
    }

    @Test
    fun loadFailed_showsMessageOnly() {
        composeTestRule.setContent {
            BlueCardApp(MainActivityUiState.LoadFailed, onDismissDamagedProgressNotice = {})
        }

        composeTestRule.onNodeWithText(
            "Couldn't load your data. Try closing and reopening BlueCard."
        ).assertIsDisplayed().assert(isPaneTitledWithItsText)
        composeTestRule.onNodeWithText("Welcome to BlueCard").assertDoesNotExist()
        composeTestRule.onNodeWithText("Merit badges").assertDoesNotExist()
    }
}
