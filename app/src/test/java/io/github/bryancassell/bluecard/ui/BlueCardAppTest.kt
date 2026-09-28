package io.github.bryancassell.bluecard.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.MainActivityUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Loading state. MainActivityTest covers Ready, which needs Hilt for the screens. */
@RunWith(AndroidJUnit4::class)
class BlueCardAppTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loading_showsNeitherOnboardingNorHome() {
        composeTestRule.setContent { BlueCardApp(MainActivityUiState.Loading) }

        composeTestRule.onNodeWithText("Welcome to BlueCard").assertDoesNotExist()
        composeTestRule.onNodeWithText("Home").assertDoesNotExist()
    }
}
