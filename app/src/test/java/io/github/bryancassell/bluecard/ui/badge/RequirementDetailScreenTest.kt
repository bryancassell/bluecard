package io.github.bryancassell.bluecard.ui.badge

import androidx.activity.ComponentDialog
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.testing.BackPresses
import io.github.bryancassell.bluecard.testing.paragraphDirection
import io.github.bryancassell.bluecard.ui.TaskFailure
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/** One test per UI state and interaction, with fixed UI state. */
@RunWith(AndroidJUnit4::class)
class RequirementDetailScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /** What the page reads as today when the picker opens, which a test can move on. */
    private var today = LocalDate.of(2026, 5, 20)

    private val openedRequirements = mutableListOf<String>()
    private val openedTrackerEntries = mutableListOf<Pair<Long?, Int?>>()
    private val completedChanges = mutableListOf<Boolean>()
    private val dateChanges = mutableListOf<LocalDate?>()
    private var commentsSaved = 0
    private var clears = 0
    private var discards = 0
    private val back = BackPresses()
    private val saveFailuresShown = mutableListOf<TaskFailure>()
    private val comment = TextFieldState()

    /** A requirement with sub-requirements. */
    private val ready = RequirementDetailUiState.Ready(
        badgeName = "Camping",
        requirement = RequirementItem(
            "2",
            "Do two of these.",
            Choice(2, 3),
            false,
            markedByHand = false
        ),
        completedDate = null,
        children = listOf(
            RequirementItem("2a", "Cook a meal.", null, completed = true, markedByHand = true),
            RequirementItem(
                "2b",
                "Lead one hike.",
                Choice(1, 2),
                completed = false,
                markedByHand = false
            ),
            RequirementItem(
                "2c",
                "Keep a camping log.",
                null,
                completed = false,
                markedByHand = true,
                TrackerCount(3, null, "nights")
            )
        ),
        tracker = null,
        commentChanged = false,
        canClear = false
    )

    /** A requirement without sub-requirements, not completed. */
    private val leaf = RequirementDetailUiState.Ready(
        badgeName = "Camping",
        requirement = RequirementItem("1", "Plan a campout.", null, false, markedByHand = true),
        completedDate = null,
        children = emptyList(),
        tracker = null,
        commentChanged = false,
        canClear = false
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

    /**
     * A requirement with a tracker of three weeks, the second filled in. Its rows decide whether
     * it's complete.
     */
    private val withWeeks = leaf.copy(
        requirement = RequirementItem(
            "2",
            "Keep a budget.",
            null,
            false,
            markedByHand = false,
            completesFromRows = true
        ),
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

    /** A requirement with sub-requirements that asks for work of its own too, not done yet. */
    private val withOwnWork = ready.copy(
        requirement = ready.requirement.copy(ownWork = OwnWork("Pack your gear.", false))
    )

    /** [withOwnWork] with its own work marked complete, but not enough sub-requirements. */
    private val ownWorkDone = withOwnWork.copy(
        requirement = ready.requirement.copy(ownWork = OwnWork("Pack your gear.", true)),
        completedDate = LocalDate.of(2026, 4, 15)
    )

    private val completedLeaf = leaf.copy(
        requirement = leaf.requirement.copy(completed = true),
        completedDate = LocalDate.of(2026, 4, 15)
    )

    private fun show(uiState: RequirementDetailUiState) {
        composeTestRule.setContent {
            back.Content {
                RequirementDetailScreen(
                    uiState = uiState,
                    comment = comment,
                    onOpenRequirement = { openedRequirements += it },
                    onOpenTrackerEntry = { entryId, rowNumber ->
                        openedTrackerEntries += entryId to rowNumber
                    },
                    onCompletedChange = { completedChanges += it },
                    onCompletedDateChange = { dateChanges += it },
                    today = { today },
                    onSaveComment = { commentsSaved++ },
                    onClear = { clears++ },
                    onDiscard = { discards++ },
                    onSaveFailureShown = { saveFailuresShown += it }
                )
            }
        }
    }

    // Each row merges its texts, so a row is the node with the requirement's summary. It's
    // scrolled to first, as the page can be taller than the screen.
    private fun row(summary: String) = composeTestRule.onNodeWithText(summary).performScrollTo()

    // The requirement's own checkbox, labeled by the text next to it.
    private fun completedCheckbox() =
        composeTestRule.onNode(hasText("Completed") and isToggleable()).performScrollTo()

    private fun commentField() =
        composeTestRule.onNode(hasSetTextAction() and hasText("Notes")).performScrollTo()

    private fun saveCommentButton() = composeTestRule.onNodeWithText("Save notes").performScrollTo()

    private fun clearButton() = composeTestRule.onNodeWithText("Clear progress").performScrollTo()

    private fun confirmClear() =
        composeTestRule.onNode(hasText("Clear") and hasAnyAncestor(isDialog())).performClick()

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
    fun requirementNoLongerNeeded_isLabeledNotNeeded() {
        show(ready.copy(requirement = ready.requirement.copy(notNeeded = true)))

        composeTestRule.onNodeWithText("Not needed").assertIsDisplayed()
    }

    @Test
    fun neededRequirement_isNotLabeledNotNeeded() {
        show(ready)

        composeTestRule.onNodeWithText("Not needed").assertDoesNotExist()
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

    // Its own work has the checkbox, and its sub-requirements are listed under it.
    @Test
    fun requirementWithOwnWork_hasCheckboxForIt_andNoDate() {
        show(withOwnWork)

        composeTestRule.onNode(hasText("Pack your gear.") and isToggleable()).performScrollTo()
            .assertIsOff()
        composeTestRule.onNode(hasText("Completed") and isToggleable()).assertDoesNotExist()
        composeTestRule.onNodeWithText("date", substring = true).assertDoesNotExist()
        row("Cook a meal.").assertIsDisplayed()
    }

    // The count sits right above the sub-requirements it counts, after the requirement's
    // completion and the checkbox for its own work.
    @Test
    fun neededCount_sitsRightAboveSubRequirements() {
        show(ready.copy(requirement = ready.requirement.copy(completed = true)))

        val count = composeTestRule.onNodeWithText("Do 2 of 3").getUnclippedBoundsInRoot()
        val completed = composeTestRule.onNodeWithText("Completed").getUnclippedBoundsInRoot()
        val firstRow = composeTestRule.onNodeWithText("Cook a meal.").getUnclippedBoundsInRoot()
        assertTrue(completed.bottom <= count.top)
        assertTrue(count.bottom <= firstRow.top)
    }

    @Test
    fun neededCount_sitsBelowOwnWorkCheckbox() {
        show(withOwnWork)

        val count = composeTestRule.onNodeWithText("Do 2 of 3").getUnclippedBoundsInRoot()
        val checkbox = composeTestRule.onNode(hasText("Pack your gear.") and isToggleable())
            .getUnclippedBoundsInRoot()
        val firstRow = composeTestRule.onNodeWithText("Cook a meal.").getUnclippedBoundsInRoot()
        assertTrue(checkbox.bottom <= count.top)
        assertTrue(count.bottom <= firstRow.top)
    }

    @Test
    fun checkingOwnWork_marksItCompleted() {
        show(withOwnWork)

        composeTestRule.onNode(hasText("Pack your gear.") and isToggleable()).performScrollTo()
            .performClick()

        assertEquals(listOf(true), completedChanges)
    }

    @Test
    fun ownWorkDone_isCheckedWithItsDate_butRequirementIsNotLabeledCompleted() {
        show(ownWorkDone)

        composeTestRule.onNode(hasText("Pack your gear.") and isToggleable()).performScrollTo()
            .assertIsOn()
            .performClick()
        composeTestRule.onNodeWithText("Completed on Apr 15, 2026").performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Completed").assertDoesNotExist()
        assertEquals(listOf(false), completedChanges)
    }

    @Test
    fun completedRequirementWithOwnWork_isLabeledCompleted() {
        show(ownWorkDone.copy(requirement = ownWorkDone.requirement.copy(completed = true)))

        composeTestRule.onNodeWithText("Completed").assertIsDisplayed()
    }

    @Test
    fun subRequirements_showNeededCountAndCompletion() {
        show(ready)

        row("Cook a meal.").assert(hasStateDescription("Completed"))
        row("Keep a camping log.").assert(hasStateDescription("Not completed"))
        row(
            "Lead one hike."
        ).assert(hasText("Do 1 of 2")).assert(hasStateDescription("Not completed"))
    }

    @Test
    fun subRequirementNoLongerNeeded_saysSoOnce() {
        val children = ready.children.map {
            if (it.number == "2c") it.copy(notNeeded = true) else it
        }
        show(ready.copy(children = children))

        // It shows "Not needed" too, but screen readers read it only as its state.
        row("Keep a camping log.")
            .assert(hasStateDescription("Not needed"))
            .assert(!hasText("Not needed"))
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

        row("Lead one hike.").assert(hasStateDescription("Completed"))
    }

    @Test
    fun subRequirementWithTracker_showsHowMuchIsFilledIn() {
        show(ready)

        row("Keep a camping log.").assert(hasText("3 nights"))
    }

    /** Sub-requirements with these numbers, each summarized as "Summary of <number>.". */
    private fun withSubRequirements(numbers: List<String>) = ready.copy(
        children = numbers.map {
            RequirementItem(it, "Summary of $it.", null, completed = false, markedByHand = true)
        }
    )

    // Like Emergency Preparedness 1b's: too long for the box's minimum width, and not all as long
    // as each other.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun subRequirementNumbersOfDifferentWidths_summariesLineUp() {
        val numbers = listOf("1b(9)", "1b(10)", "1b(21)")
        show(withSubRequirements(numbers))

        // In the unmerged tree, each summary is a node of its own.
        val starts = numbers.map {
            composeTestRule.onNodeWithText("Summary of $it.", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
                .left
        }
        assertEquals(List(starts.size) { starts.first() }, starts)
    }

    // Archery's are the longest numbers in the catalog.
    private val longestNumbers = listOf("5A(6)(a)(1)", "5A(6)(a)(2)", "5A(6)(a)(3)", "5A(6)(a)(4)")

    private fun assertShownInFullOnOneLine(numbers: List<String>) {
        for (number in numbers) {
            val layouts = mutableListOf<TextLayoutResult>()
            composeTestRule.onNodeWithText(number, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertEquals("Lines in $number", 1, layout.lineCount)
            // Not hasVisualOverflow: the layout a Text reports is laid out across all the width
            // it was offered, so it overflows the text's own width even when the text fits.
            assertTrue(
                "Expected none of $number cut off",
                layout.getLineRight(0) <= layout.size.width && !layout.didOverflowHeight
            )
        }
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun longestNumbers_showInFullOnOneLine() {
        show(withSubRequirements(longestNumbers))

        assertShownInFullOnOneLine(longestNumbers)
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(fontScale = 2f)
    @Test
    fun longestNumbers_atLargestFontSize_showInFullOnOneLine() {
        show(withSubRequirements(longestNumbers))

        assertShownInFullOnOneLine(longestNumbers)
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
    fun logEntry_showsALineBreakInAValueAsASpace() {
        val values = listOf(
            // Only an import can store a line break in single-line text (#156).
            TrackerValue(TrackerColumnType.TEXT, "Push-ups\nand sit-ups"),
            TrackerValue(TrackerColumnType.MULTILINE_TEXT, "3 sets of 10\nFelt good")
        )
        show(
            withLog.copy(tracker = withLog.tracker?.copy(rows = listOf(TrackerRow(1, 11, values))))
        )

        row("Session 1").assert(hasText("Push-ups and sit-ups · 3 sets of 10 Felt good"))
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

    // Its date field only shows once it's complete (the next tests).
    @Test
    fun fixedRows_haveNoCheckboxOrDate() {
        show(withWeeks)

        composeTestRule.onNodeWithText("Completed").assertDoesNotExist()
        composeTestRule.onNodeWithText("date", substring = true).assertDoesNotExist()
    }

    private val weeksFilledIn = withWeeks.copy(
        requirement = withWeeks.requirement.copy(completed = true),
        completedDate = LocalDate.of(2026, 4, 15)
    )

    @Test
    fun fixedRowsAllFilledIn_areLabeledCompleted_withTheirDateButNoCheckbox() {
        show(weeksFilledIn)

        composeTestRule.onNodeWithText("Completed").assertIsDisplayed()
        composeTestRule.onNode(hasText("Completed") and isToggleable()).assertDoesNotExist()
        composeTestRule.onNodeWithText("Completed on Apr 15, 2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remove date").assertIsDisplayed()
    }

    @Test
    fun fixedRowsAllFilledIn_changeDate_picksAnotherDay() {
        show(weeksFilledIn)

        composeTestRule.onNodeWithText("Change date").performClick()
        pickerDay("April 10, 2026").performClick()
        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals(listOf<LocalDate?>(LocalDate.of(2026, 4, 10)), dateChanges)
    }

    @Test
    fun fixedRowsAllFilledIn_withoutDate_offerToAddOne() {
        show(weeksFilledIn.copy(completedDate = null))

        composeTestRule.onNodeWithText("No completion date").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add date").performClick()
        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals(listOf<LocalDate?>(today), dateChanges)
    }

    @Test
    fun log_keepsItsCheckbox() {
        show(withLog)

        completedCheckbox().assertIsOff()
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
    fun addDate_onAPageOpenPastMidnight_opensAtTheNewDay() {
        show(completedLeaf.copy(completedDate = null))
        today = LocalDate.of(2026, 5, 21)

        composeTestRule.onNodeWithText("Add date").performScrollTo().performClick()
        pickerDay("May 22, 2026").assertIsNotEnabled()
        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals(listOf<LocalDate?>(LocalDate.of(2026, 5, 21)), dateChanges)
    }

    @Test
    fun changeDate_onAPageOpenPastMidnight_offersTheNewDay() {
        show(completedLeaf.copy(completedDate = LocalDate.of(2026, 5, 18)))
        today = LocalDate.of(2026, 5, 21)

        composeTestRule.onNodeWithText("Change date").performScrollTo().performClick()
        pickerDay("May 22, 2026").assertIsNotEnabled()
        pickerDay("May 21, 2026").performClick()
        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals(listOf<LocalDate?>(LocalDate.of(2026, 5, 21)), dateChanges)
    }

    // As when it was recorded while the device's clock was ahead.
    @Test
    fun changeDate_ofADateAfterToday_opensAtToday() {
        show(completedLeaf.copy(completedDate = LocalDate.of(2026, 5, 22)))

        composeTestRule.onNodeWithText("Change date").performScrollTo().performClick()
        pickerDay(
            "May 20, 2026"
        ).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals(listOf<LocalDate?>(today), dateChanges)
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
    fun back_withChangedComment_asksBeforeDiscardingIt() {
        show(completedLeaf.copy(commentChanged = true))

        back.press(composeTestRule)
        // Only the notes: the checkbox and date are saved already.
        composeTestRule.onNodeWithText("Your changes to the notes haven't been saved.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Discard").performClick()

        assertEquals(1, discards)
        assertEquals(0, back.closes)
    }

    @Test
    fun back_withUnchangedComment_closesThePage() {
        show(completedLeaf)

        back.press(composeTestRule)

        assertEquals(1, back.closes)
        composeTestRule.onNodeWithText("Discard changes?").assertDoesNotExist()
    }

    @Test
    fun saveFailed_showsMessage_thenReportsItShown() {
        val failure = TaskFailure()
        show(leaf.copy(saveFailure = failure))

        composeTestRule.onNodeWithText("Couldn't save. Try again.").assertIsDisplayed()
        assertEquals(emptyList<TaskFailure>(), saveFailuresShown)

        // A short snackbar shows for 4 seconds.
        composeTestRule.mainClock.advanceTimeBy(5_000)

        composeTestRule.onNodeWithText("Couldn't save. Try again.").assertDoesNotExist()
        assertEquals(listOf(failure), saveFailuresShown)
    }

    @Test
    fun nothingRecorded_hasNoClearButton() {
        show(leaf)

        composeTestRule.onNodeWithText("Clear progress").assertDoesNotExist()
    }

    @Test
    fun clear_asksFirst_thenClears() {
        show(completedLeaf.copy(canClear = true))

        clearButton().performClick()
        composeTestRule.onNodeWithText("Clear progress on requirement 1?").assertIsDisplayed()
        composeTestRule.onNodeWithText("What you recorded for it will be removed.")
            .assertIsDisplayed()
        assertEquals(0, clears)
        confirmClear()

        assertEquals(1, clears)
        composeTestRule.onNodeWithText("Clear progress on requirement 1?").assertDoesNotExist()
    }

    @Test
    fun clear_onRequirementWithSubRequirements_saysTheyAreClearedToo() {
        show(ready.copy(canClear = true))

        clearButton().performClick()

        composeTestRule.onNodeWithText("Clear progress on requirement 2?").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "What you recorded for it and the requirements under it will be removed."
            )
            .assertIsDisplayed()
    }

    @Test
    fun clear_withUnsavedNotes_saysTheyAreDiscarded() {
        show(completedLeaf.copy(commentChanged = true, canClear = true))

        clearButton().performClick()

        composeTestRule
            .onNodeWithText(
                "What you recorded for it will be removed, along with unsaved changes to its " +
                    "notes."
            )
            .assertIsDisplayed()
    }

    @Test
    fun clear_onRequirementWithSubRequirements_withUnsavedNotes_saysBoth() {
        show(ready.copy(commentChanged = true, canClear = true))

        clearButton().performClick()

        composeTestRule
            .onNodeWithText(
                "What you recorded for it and the requirements under it will be removed, " +
                    "along with unsaved changes to its notes."
            )
            .assertIsDisplayed()
    }

    @Test
    fun clear_cancel_clearsNothing() {
        show(completedLeaf.copy(canClear = true))

        clearButton().performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()

        assertEquals(0, clears)
        composeTestRule.onNodeWithText("Clear progress on requirement 1?").assertDoesNotExist()
    }

    @Test
    fun clear_back_closesTheDialog_andClearsNothing() {
        show(completedLeaf.copy(canClear = true))

        clearButton().performClick()
        // Espresso's pressBack doesn't reach the dialog's window under Robolectric, so Back is
        // sent to the dialog itself.
        composeTestRule.runOnIdle {
            (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher
                .onBackPressed()
        }

        assertEquals(0, clears)
        composeTestRule.onNodeWithText("Clear progress on requirement 1?").assertDoesNotExist()
        composeTestRule.onNodeWithText("Clear progress").assertExists()
    }
}
