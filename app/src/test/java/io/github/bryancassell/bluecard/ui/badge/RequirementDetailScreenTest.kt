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
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.testing.paragraphDirection
import io.github.bryancassell.bluecard.ui.SaveFailure
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
    private val openedTrackerEntries = mutableListOf<Pair<Long?, Int?>>()
    private val completedChanges = mutableListOf<Boolean>()
    private val dateChanges = mutableListOf<LocalDate?>()
    private var commentsSaved = 0
    private val saveFailuresShown = mutableListOf<SaveFailure>()
    private val comment = TextFieldState()

    /** A requirement with sub-requirements. */
    private val ready = RequirementDetailUiState.Ready(
        badgeName = "Camping",
        requirement = RequirementItem("2", "Do two of these.", Choice(2, 3), false, true),
        completedDate = null,
        children = listOf(
            RequirementItem("2a", "Cook a meal.", null, completed = true, false),
            RequirementItem("2b", "Lead one hike.", Choice(1, 2), completed = false, true),
            RequirementItem(
                "2c",
                "Keep a camping log.",
                null,
                completed = false,
                false,
                TrackerCount(3, null, "nights")
            )
        ),
        tracker = null,
        commentChanged = false,
        today = today
    )

    /** A requirement without sub-requirements, not completed. */
    private val leaf = RequirementDetailUiState.Ready(
        badgeName = "Camping",
        requirement = RequirementItem("1", "Plan a campout.", null, false, false),
        completedDate = null,
        children = emptyList(),
        tracker = null,
        commentChanged = false,
        today = today
    )

    /** A requirement with a log, a tracker the scout adds rows to. */
    private val withLog = leaf.copy(
        tracker = TrackerItem(
            count = TrackerCount(2, null, "sessions"),
            rowTitle = "Session",
            rowLabel = "session",
            rows = listOf(
                TrackerRow(
                    1,
                    11,
                    listOf(
                        TrackerValue(TrackerColumnType.DATE, "2026-04-12"),
                        TrackerValue(TrackerColumnType.TEXT, "Running"),
                        TrackerValue(TrackerColumnType.NUMBER, "30")
                    )
                ),
                TrackerRow(
                    2,
                    12,
                    listOf(
                        // Shown as it is, since it isn't a date.
                        TrackerValue(TrackerColumnType.DATE, "Last Tuesday"),
                        TrackerValue(TrackerColumnType.TEXT, "Swimming")
                    )
                )
            )
        )
    )

    /** A requirement with a tracker of three weeks, the second filled in. */
    private val withWeeks = leaf.copy(
        tracker = TrackerItem(
            count = TrackerCount(1, 3, "weeks"),
            rowTitle = "Week",
            rowLabel = "week",
            rows = listOf(
                TrackerRow(1, null, emptyList()),
                TrackerRow(2, 5, listOf(TrackerValue(TrackerColumnType.NUMBER, "20"))),
                TrackerRow(3, null, emptyList())
            )
        )
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
                onOpenTrackerEntry = { entryId, rowNumber ->
                    openedTrackerEntries += entryId to rowNumber
                },
                onCompletedChange = { completedChanges += it },
                onCompletedDateChange = { dateChanges += it },
                onSaveComment = { commentsSaved++ },
                onSaveFailureShown = { saveFailuresShown += it }
            )
        }
    }

    // Each row merges its texts, so a row is the node with the requirement's summary. It's
    // scrolled to first, as the page can be taller than the screen.
    private fun row(summary: String) = composeTestRule.onNodeWithText(summary).performScrollTo()

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

    // No checkbox for the requirement or on its sub-requirements' rows: the scout marks each
    // sub-requirement complete on its own page.
    @Test
    fun requirementWithSubRequirements_hasNoCheckboxOrDate() {
        show(ready)

        composeTestRule.onNode(isToggleable(), useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("date", substring = true).assertDoesNotExist()
    }

    @Test
    fun subRequirements_showNeededCountAndCompletion() {
        show(ready)

        row("Cook a meal.").assert(hasContentDescription("Completed"))
        row("Keep a camping log.").assert(!hasContentDescription("Completed"))
        row(
            "Lead one hike."
        ).assert(hasText("Do 1 of 2")).assert(!hasContentDescription("Completed"))
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
    fun subRequirementWithTracker_showsHowMuchIsFilledIn() {
        show(ready)

        row("Keep a camping log.").assert(hasText("3 nights"))
    }

    @Test
    fun noTracker_showsNoTrackerRows() {
        show(leaf)

        composeTestRule.onNodeWithText("Add", substring = true).assertDoesNotExist()
    }

    @Test
    fun log_showsCountAsHeading_thenItsEntries() {
        show(withLog)

        composeTestRule.onNodeWithText("2 sessions").performScrollTo().assert(isHeading())
        row("Session 1").assert(hasText("Apr 12, 2026 · Running · 30"))
        row("Session 2").assert(hasText("Last Tuesday · Swimming"))
    }

    @Test
    fun logEntry_isButtonThatOpensIt() {
        show(withLog)

        row("Session 2")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()

        assertEquals(listOf<Pair<Long?, Int?>>(12L to 2), openedTrackerEntries)
    }

    @Test
    fun addToLog_opensANewEntry() {
        show(withLog)

        composeTestRule.onNodeWithText("Add session").performScrollTo().performClick()

        assertEquals(listOf<Pair<Long?, Int?>>(null to null), openedTrackerEntries)
    }

    @Test
    fun fixedRows_listsEveryRow_withNoAddButton() {
        show(withWeeks)

        composeTestRule.onNodeWithText("1 of 3 weeks").performScrollTo().assert(isHeading())
        row("Week 1").assert(!hasText("20"))
        row("Week 2").assert(hasText("20"))
        row("Week 3")
        composeTestRule.onNodeWithText("Add week").assertDoesNotExist()
    }

    @Test
    fun fixedRow_opensWithItsNumber_andItsEntryIfFilledIn() {
        show(withWeeks)

        row("Week 3").performClick()
        row("Week 2").performClick()

        assertEquals(listOf<Pair<Long?, Int?>>(null to 3, 5L to 2), openedTrackerEntries)
    }

    @Test
    fun everySubRequirement_isButtonThatOpensIt() {
        show(ready)

        for (summary in listOf("Cook a meal.", "Lead one hike.", "Keep a camping log.")) {
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

        assertEquals(listOf(true), completedChanges)
    }

    @Test
    fun uncheckingLeaf_marksItNotCompleted() {
        show(completedLeaf)

        completedCheckbox().assertIsOn().performClick()

        assertEquals(listOf(false), completedChanges)
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

    // The layout is left-to-right, like the English strings, but a comment typed in Persian
    // reads right-to-left, with its final period at its end.
    @Test
    fun commentTypedInPersian_readsRightToLeft() {
        show(leaf)

        commentField().performTextInput("با گشتی‌ام برنامه‌ریزی کردم.")

        assertEquals(ResolvedTextDirection.Rtl, commentField().paragraphDirection())
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
        val failure = SaveFailure()
        show(leaf.copy(saveFailure = failure))

        composeTestRule.onNodeWithText("Couldn't save. Try again.").assertIsDisplayed()
        assertEquals(emptyList<SaveFailure>(), saveFailuresShown)

        // A short snackbar shows for 4 seconds.
        composeTestRule.mainClock.advanceTimeBy(5_000)

        composeTestRule.onNodeWithText("Couldn't save. Try again.").assertDoesNotExist()
        assertEquals(listOf(failure), saveFailuresShown)
    }
}
