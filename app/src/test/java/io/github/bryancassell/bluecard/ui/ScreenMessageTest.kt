package io.github.bryancassell.bluecard.ui

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The screens' tests cover which message each one shows. */
@RunWith(AndroidJUnit4::class)
class ScreenMessageTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun message_isShownAndAnnouncedToScreenReaders() {
        composeTestRule.setContent { ScreenMessage("Something went wrong.") }

        composeTestRule.onNodeWithText("Something went wrong.")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )
    }
}
