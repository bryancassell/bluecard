package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
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
class RequirementDetailScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val openedRequirements = mutableListOf<String>()

    private val ready = RequirementDetailUiState.Ready(
        badgeName = "Camping",
        requirement = RequirementItem("2", "Do two of these.", Choice(2, 3), false, true),
        children = listOf(
            RequirementItem("2a", "Cook a meal.", null, completed = true, opensDetail = false),
            RequirementItem(
                "2b",
                "Lead one hike.",
                Choice(1, 2),
                completed = false,
                opensDetail = true
            ),
            RequirementItem("2c", "Pitch a tent.", null, completed = false, opensDetail = false)
        )
    )

    private fun show(uiState: RequirementDetailUiState) {
        composeTestRule.setContent {
            RequirementDetailScreen(
                uiState = uiState,
                onOpenRequirement = { openedRequirements += it }
            )
        }
    }

    // Each row merges its texts, so a row is the node with the requirement's summary. It's
    // scrolled to first, as the page can be taller than the screen.
    private fun row(summary: String) = composeTestRule.onNodeWithText(summary).performScrollTo()

    @Test
    fun loading_showsProgressOnly() {
        show(RequirementDetailUiState.Loading)

        composeTestRule
            .onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo.Indeterminate
                )
            )
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Requirement", substring = true).assertDoesNotExist()
    }

    @Test
    fun unavailable_showsMessageOnly() {
        show(RequirementDetailUiState.Unavailable)

        composeTestRule
            .onNodeWithText("This badge's requirements aren't in this version of BlueCard.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Requirement 2").assertDoesNotExist()
    }

    @Test
    fun ready_showsRequirement() {
        show(ready)

        composeTestRule.onNodeWithText("Camping").assertIsDisplayed()
        composeTestRule.onNodeWithText("Requirement 2").assert(isHeading()).assertIsDisplayed()
        composeTestRule.onNodeWithText("Do two of these.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Do 2 of 3").assertIsDisplayed()
    }

    @Test
    fun notCompletedRequirement_isNotLabeledCompleted() {
        show(ready)

        composeTestRule.onNodeWithText("Completed").assertDoesNotExist()
    }

    @Test
    fun completedRequirement_isLabeledCompleted() {
        show(ready.copy(requirement = ready.requirement.copy(completed = true)))

        composeTestRule.onNodeWithText("Completed").assertIsDisplayed()
    }

    @Test
    fun subRequirements_showNeededCountAndCompletion() {
        show(ready)

        row("Cook a meal.").assert(hasContentDescription("Completed"))
        row(
            "Lead one hike."
        ).assert(hasText("Do 1 of 2")).assert(!hasContentDescription("Completed"))
        row("Pitch a tent.").assert(!hasContentDescription("Completed"))
    }

    @Test
    fun subRequirementWithMore_isButtonThatOpensIt() {
        show(ready)

        row("Lead one hike.")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()

        assertEquals(listOf("2b"), openedRequirements)
    }

    @Test
    fun subRequirementWithoutMore_doesNotOpen() {
        show(ready)

        row("Cook a meal.").assert(!hasClickAction())
        row("Pitch a tent.").assert(!hasClickAction())
    }
}
