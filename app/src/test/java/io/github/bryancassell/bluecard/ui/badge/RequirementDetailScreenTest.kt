package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** One test per UI state and interaction, with fixed UI state. */
@RunWith(AndroidJUnit4::class)
class RequirementDetailScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val today = LocalDate.of(2026, 5, 20)

    private val openedRequirements = mutableListOf<String>()
    private val completedChanges = mutableListOf<Pair<String, Boolean>>()
    private val dateChanges = mutableListOf<LocalDate?>()
    private var commentsSaved = 0
    private var saveFailuresShown = 0
    private val comment = TextFieldState()

    /** A requirement with sub-requirements. */
    private val ready = RequirementDetailUiState.Ready(
        badgeName = "Camping",
        requirement = RequirementItem("2", "Do two of these.", Choice(2, 3), false, true),
        completedDate = null,
        children = listOf(
            RequirementItem("2a", "Cook a meal.", null, completed = true, false),
            RequirementItem("2b", "Lead one hike.", Choice(1, 2), completed = false, true),
            RequirementItem("2c", "Pitch a tent.", null, completed = false, false)
        ),
        commentChanged = false,
        today = today
    )

    /** A requirement without sub-requirements, not completed. */
    private val leaf = RequirementDetailUiState.Ready(
        badgeName = "Camping",
        requirement = RequirementItem("1", "Plan a campout.", null, false, false),
        completedDate = null,
        children = emptyList(),
        commentChanged = false,
        today = today
    )

    private val completedLeaf = leaf.copy(
        requirement = leaf.requirement.copy(completed = true),
        completedDate = LocalDate.of(2026, 4, 15)
    )

    private fun show(uiState: RequirementDetailUiState) {
        composeTestRule.setContent {
            RequirementDetailScreen(
                uiState = uiState,
                comment = comment,
                onOpenRequirement = { openedRequirements += it },
                onCompletedChange = { number, completed ->
                    completedChanges += number to completed
                },
                onCompletedDateChange = { dateChanges += it },
                onSaveComment = { commentsSaved++ },
                onSaveFailureShown = { saveFailuresShown++ }
            )
        }
    }

    // Each row merges its texts, so a row is the node with the requirement's summary. It's
    // scrolled to first, as the page can be taller than the screen.
    private fun row(summary: String) = composeTestRule.onNodeWithText(summary).performScrollTo()

    private fun rowCheckbox(number: String) = composeTestRule
        .onNode(hasContentDescription("Requirement $number completed") and isToggleable())
        .performScrollTo()

    // The requirement's own checkbox, labeled by the text next to it.
    private fun completedCheckbox() =
        composeTestRule.onNode(hasText("Completed") and isToggleable()).performScrollTo()

    private fun commentField() =
        composeTestRule.onNode(hasSetTextAction() and hasText("Comment")).performScrollTo()

    private fun saveCommentButton() =
        composeTestRule.onNodeWithText("Save comment").performScrollTo()

    // A day in the date picker, which reads each day as its full date.
    private fun pickerDay(date: String) =
        composeTestRule.onNode(hasText(date, substring = true) and hasClickAction())

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
            .onNodeWithText("This requirement isn't in the requirements this badge uses.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Requirement 2").assertDoesNotExist()
    }

    @Test
    fun loadFailed_showsMessageOnly() {
        show(RequirementDetailUiState.LoadFailed)

        composeTestRule.onNodeWithText(
            "Couldn't load your data. Try closing and reopening BlueCard."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Requirement", substring = true).assertDoesNotExist()
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
    fun requirementWithSubRequirements_hasNoCheckboxOrDate() {
        show(ready)

        composeTestRule.onNode(hasText("Completed") and isToggleable()).assertDoesNotExist()
        composeTestRule.onNodeWithText("date", substring = true).assertDoesNotExist()
    }

    @Test
    fun subRequirements_showNeededCountAndCompletion() {
        show(ready)

        rowCheckbox("2a").assertIsOn()
        rowCheckbox("2c").assertIsOff()
        row(
            "Lead one hike."
        ).assert(hasText("Do 1 of 2")).assert(!hasContentDescription("Completed"))
        composeTestRule.onNode(
            hasContentDescription("Requirement 2b completed")
        ).assertDoesNotExist()
    }

    @Test
    fun completedSubRequirementWithMore_isChecked() {
        val children = ready.children.map {
            if (it.number ==
                "2b"
            ) {
                it.copy(completed = true)
            } else {
                it
            }
        }
        show(ready.copy(children = children))

        row("Lead one hike.").assert(hasContentDescription("Completed"))
    }

    @Test
    fun checkingSubRequirement_marksItCompleted() {
        show(ready)

        rowCheckbox("2c").performClick()
        rowCheckbox("2a").performClick()

        assertEquals(listOf("2c" to true, "2a" to false), completedChanges)
        assertEquals(emptyList<String>(), openedRequirements)
    }

    @Test
    fun everySubRequirement_isButtonThatOpensIt() {
        show(ready)

        for (summary in listOf("Cook a meal.", "Lead one hike.", "Pitch a tent.")) {
            row(summary)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .performClick()
        }

        assertEquals(listOf("2a", "2b", "2c"), openedRequirements)
    }

    @Test
    fun notCompletedLeaf_hasUncheckedCheckboxAndNoDate() {
        show(leaf)

        completedCheckbox().assertIsOff()
        composeTestRule.onNodeWithText("date", substring = true).assertDoesNotExist()
    }

    @Test
    fun checkingLeaf_marksItCompleted() {
        show(leaf)

        completedCheckbox().performClick()

        assertEquals(listOf("1" to true), completedChanges)
    }

    @Test
    fun uncheckingLeaf_marksItNotCompleted() {
        show(completedLeaf)

        completedCheckbox().assertIsOn().performClick()

        assertEquals(listOf("1" to false), completedChanges)
    }

    @Test
    fun completedLeafWithDate_showsDateWithChangeAndRemove() {
        show(completedLeaf)

        composeTestRule.onNodeWithText("Completed on Apr 15, 2026").performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Change date").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Remove date").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Add date").assertDoesNotExist()
    }

    @Test
    fun completedLeafWithoutDate_offersToAddOne() {
        show(completedLeaf.copy(completedDate = null))

        composeTestRule.onNodeWithText("No completion date").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Add date").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Remove date").assertDoesNotExist()
    }

    @Test
    fun removeDate_removesIt() {
        show(completedLeaf)

        composeTestRule.onNodeWithText("Remove date").performScrollTo().performClick()

        assertEquals(listOf<LocalDate?>(null), dateChanges)
    }

    @Test
    fun changeDate_picksAnotherDay() {
        show(completedLeaf)

        composeTestRule.onNodeWithText("Change date").performScrollTo().performClick()
        // It opens at the current date.
        pickerDay(
            "April 15, 2026"
        ).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        pickerDay("April 10, 2026").performClick()
        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals(listOf<LocalDate?>(LocalDate.of(2026, 4, 10)), dateChanges)
        composeTestRule.onNodeWithText("OK").assertDoesNotExist()
    }

    @Test
    fun addDate_opensAtToday() {
        show(completedLeaf.copy(completedDate = null))

        composeTestRule.onNodeWithText("Add date").performScrollTo().performClick()
        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals(listOf<LocalDate?>(today), dateChanges)
    }

    @Test
    fun datePicker_doesNotOfferFutureDates() {
        show(completedLeaf.copy(completedDate = null))

        composeTestRule.onNodeWithText("Add date").performScrollTo().performClick()

        pickerDay("May 20, 2026").assertIsEnabled()
        pickerDay("May 21, 2026").assertIsNotEnabled()
    }

    @Test
    fun datePicker_cancel_changesNothing() {
        show(completedLeaf)

        composeTestRule.onNodeWithText("Change date").performScrollTo().performClick()
        pickerDay("April 10, 2026").performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()

        assertEquals(emptyList<LocalDate?>(), dateChanges)
        composeTestRule.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test
    fun comment_isEditable() {
        show(leaf)

        commentField().performTextInput("Planned it with my patrol.")

        assertEquals("Planned it with my patrol.", comment.text.toString())
    }

    @Test
    fun requirementWithSubRequirements_hasCommentToo() {
        show(ready)

        commentField().assertIsDisplayed()
    }

    @Test
    fun comment_trimsTextPast2000Characters() {
        show(leaf)
        commentField().performTextInput("a".repeat(1_990))

        // As when pasting.
        commentField().performTextInput("b".repeat(20))

        assertEquals("a".repeat(1_990) + "b".repeat(10), comment.text.toString())
        commentField().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.MaxTextLength, 2_000)
        )
    }

    @Test
    fun unchangedComment_cannotBeSaved() {
        show(leaf)

        saveCommentButton().assertIsNotEnabled()
    }

    @Test
    fun changedComment_isSaved() {
        show(leaf.copy(commentChanged = true))

        saveCommentButton().assertIsEnabled().performClick()

        assertEquals(1, commentsSaved)
    }

    @Test
    fun saveFailed_showsMessage_thenReportsItShown() {
        show(leaf.copy(saveFailed = true))

        composeTestRule.onNodeWithText("Couldn't save. Try again.").assertIsDisplayed()
        assertEquals(0, saveFailuresShown)

        // A short snackbar shows for 4 seconds.
        composeTestRule.mainClock.advanceTimeBy(5_000)

        composeTestRule.onNodeWithText("Couldn't save. Try again.").assertDoesNotExist()
        assertEquals(1, saveFailuresShown)
    }
}
