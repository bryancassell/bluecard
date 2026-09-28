package io.github.bryancassell.bluecard.ui.home

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** One test per UI state and interaction, with fixed UI state. */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var badgesOpened = 0
    private var dataManagementOpened = 0

    private val noProgress = HomeUiState.Ready(
        name = "Alex Scout",
        unitNumber = "123",
        badges = ProgressCounts(completed = 0, inProgress = 0),
        eagle = ProgressCounts(completed = 0, inProgress = 0),
        eagleTotal = 13
    )

    private val withProgress = noProgress.copy(
        badges = ProgressCounts(completed = 5, inProgress = 3),
        eagle = ProgressCounts(completed = 4, inProgress = 2)
    )

    private fun show(uiState: HomeUiState) {
        composeTestRule.setContent {
            HomeScreen(
                uiState = uiState,
                onOpenBadges = { badgesOpened++ },
                onOpenDataManagement = { dataManagementOpened++ }
            )
        }
    }

    private fun text(text: String) = composeTestRule.onNodeWithText(text)

    private val loadingIndicator = SemanticsMatcher.expectValue(
        SemanticsProperties.ProgressBarRangeInfo,
        ProgressBarRangeInfo.Indeterminate
    )

    private fun eagleBar(fraction: Float) = SemanticsMatcher.expectValue(
        SemanticsProperties.ProgressBarRangeInfo,
        ProgressBarRangeInfo(fraction, 0f..1f)
    )

    @Test
    fun loading_showsProgressAndNoProfile() {
        show(HomeUiState.Loading)

        composeTestRule.onNode(loadingIndicator).assertIsDisplayed()
        text("Merit badges").assertDoesNotExist()
    }

    @Test
    fun ready_showsNameAsHeadingAndUnit() {
        show(noProgress)

        text("Alex Scout").assert(isHeading()).assertIsDisplayed()
        text("Unit 123").assertIsDisplayed()
        composeTestRule.onNode(loadingIndicator).assertDoesNotExist()
    }

    @Test
    fun noProgress_showsMessageAndNoSummary() {
        show(noProgress)

        text("You haven't started any merit badges yet.").assertIsDisplayed()
        text("Your merit badges").assertDoesNotExist()
        text("Eagle-required").assertDoesNotExist()
    }

    @Test
    fun withProgress_showsBadgeCounts() {
        show(withProgress)

        text("You haven't started any merit badges yet.").assertDoesNotExist()
        text("Your merit badges").assert(isHeading()).assertIsDisplayed()
        text("5 completed").assertIsDisplayed()
        text("3 in progress").assertIsDisplayed()
    }

    @Test
    fun withProgress_showsEagleProgress() {
        show(withProgress)

        text("Eagle-required").assert(isHeading()).assertIsDisplayed()
        text("4 of 13 completed").assertIsDisplayed()
        composeTestRule.onNode(eagleBar(4f / 13)).assertIsDisplayed()
        text("2 in progress").assertIsDisplayed()
    }

    @Test
    fun withProgress_noEagleBadgesInCatalog_hidesEagleProgress() {
        show(
            withProgress.copy(
                eagle = ProgressCounts(completed = 0, inProgress = 0),
                eagleTotal = 0
            )
        )

        text("Your merit badges").assertIsDisplayed()
        text("Eagle-required").assertDoesNotExist()
    }

    @Test
    fun meritBadgesButton_opensBadges() {
        show(withProgress)

        text("Merit badges").performScrollTo().performClick()

        assertEquals(1, badgesOpened)
        assertEquals(0, dataManagementOpened)
    }

    @Test
    fun manageDataButton_opensDataManagement() {
        show(withProgress)

        text("Manage data").performScrollTo().performClick()

        assertEquals(1, dataManagementOpened)
        assertEquals(0, badgesOpened)
    }
}
