package io.github.bryancassell.bluecard.ui.badge

import androidx.activity.ComponentDialog
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.toSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.catalog.ColumnTotal
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.MeritBadgesNeeded
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.progress.Completion
import io.github.bryancassell.bluecard.data.progress.EarnedBadge
import io.github.bryancassell.bluecard.data.progress.MeritBadgeCredit
import io.github.bryancassell.bluecard.data.progress.TimeInRank
import io.github.bryancassell.bluecard.data.progress.TrackerTotal
import io.github.bryancassell.bluecard.testing.AccessibilityChecks
import io.github.bryancassell.bluecard.testing.BackPresses
import io.github.bryancassell.bluecard.testing.OnScreenKeyboard
import io.github.bryancassell.bluecard.testing.SMALL_PHONE
import io.github.bryancassell.bluecard.testing.assertAnnouncedWhenShown
import io.github.bryancassell.bluecard.testing.paragraphDirection
import io.github.bryancassell.bluecard.ui.TaskFailure
import java.math.BigDecimal
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
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RequirementDetailScreenTest {
    @get:Rule(order = 0)
    val composeTestRule = createComposeRule()

    @get:Rule(order = 1)
    val accessibilityChecks = AccessibilityChecks(composeTestRule)

    /** What the page reads as today when the picker opens, which a test can move on. */
    private var today = LocalDate.of(2026, 5, 20)

    private val openedRequirements = mutableListOf<String>()
    private val openedTrackerEntries = mutableListOf<Pair<Long?, Int?>>()
    private val openedBadges = mutableListOf<String>()
    private val completedChanges = mutableListOf<Boolean>()
    private val dateChanges = mutableListOf<LocalDate?>()
    private var saves = 0
    private var clears = 0
    private var discards = 0
    private val back = BackPresses()
    private val saveFailuresShown = mutableListOf<TaskFailure>()
    private val keyboard = OnScreenKeyboard(composeTestRule)
    private val signedOffBy = TextFieldState()
    private val comment = TextFieldState()

    /** A requirement with sub-requirements. */
    private val ready = RequirementDetailUiState.Ready(
        advancementName = "Camping",
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
        textChanged = false,
        canClear = false
    )

    /** A requirement without sub-requirements, not completed. */
    private val leaf = RequirementDetailUiState.Ready(
        advancementName = "Camping",
        requirement = RequirementItem("1", "Plan a campout.", null, false, markedByHand = true),
        completedDate = null,
        children = emptyList(),
        tracker = null,
        textChanged = false,
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

    /**
     * A rank's requirement that asks for four months in the rank below, which was earned on
     * Sep 1, 2026.
     */
    private val withTimeInRank = leaf.copy(
        advancementName = "Star",
        requirement = RequirementItem(
            "1",
            "Be active for four months.",
            null,
            false,
            markedByHand = true
        ),
        timeInRank = TimeInRank(
            4,
            "First Class",
            TimeInRank.Eligibility.From(LocalDate.of(2027, 1, 1))
        )
    )

    /** The UI state shown, which a test can change after [show]. */
    private var uiState by mutableStateOf<RequirementDetailUiState>(
        RequirementDetailUiState.Loading
    )

    private fun show(state: RequirementDetailUiState) {
        uiState = state
        composeTestRule.setContent {
            keyboard.Content {
                back.Content {
                    RequirementDetailScreen(
                        uiState = uiState,
                        signedOffBy = signedOffBy,
                        comment = comment,
                        onOpenRequirement = { openedRequirements += it },
                        onOpenTrackerEntry = { entryId, rowNumber ->
                            openedTrackerEntries += entryId to rowNumber
                        },
                        onOpenBadge = { openedBadges += it },
                        onCompletedChange = { completedChanges += it },
                        onCompletedDateChange = { dateChanges += it },
                        today = { today },
                        onSave = { saves++ },
                        onClear = { clears++ },
                        onDiscard = { discards++ },
                        onSaveFailureShown = { saveFailuresShown += it }
                    )
                }
            }
        }
    }

    // Each row merges its texts, so a row is the node with the requirement's summary. It's
    // scrolled to first, as the page can be taller than the screen.
    private fun row(summary: String) = composeTestRule.onNodeWithText(summary).performScrollTo()

    // The requirement's own checkbox, labeled by the text next to it.
    private fun completedCheckbox() =
        composeTestRule.onNode(hasText("Completed") and isToggleable()).performScrollTo()

    private fun commentField() = commentFieldWithoutScrolling().performScrollTo()

    /** The notes field, wherever the page has it. */
    private fun commentFieldWithoutScrolling() =
        composeTestRule.onNode(hasSetTextAction() and hasText("Notes"))

    private fun saveCommentButton() = saveCommentButtonWithoutScrolling().performScrollTo()

    private fun saveCommentButtonWithoutScrolling() = composeTestRule.onNodeWithText("Save notes")

    private fun signOffField() =
        composeTestRule.onNode(hasSetTextAction() and hasText("Signed off by")).performScrollTo()

    /** A rank's requirement without sub-requirements, which has a field for who signed off. */
    private val rankLeaf = leaf.copy(
        advancementName = "Tenderfoot",
        requirement = RequirementItem(
            "1a",
            "Pack for a campout.",
            null,
            false,
            markedByHand = true
        ),
        hasSignOffField = true
    )

    private fun clearButton() = composeTestRule.onNodeWithText("Clear progress").performScrollTo()

    private fun confirmClear() =
        composeTestRule.onNode(hasText("Clear") and hasAnyAncestor(isDialog())).performClick()

    // A day in the date picker, which reads each day as its full date.
    private fun pickerDay(date: String) =
        composeTestRule.onNode(hasText(date, substring = true) and hasClickAction())

    /**
     * Asserts none of the node is clipped, as a picker's day must not be to keep its 48dp touch
     * target. assertIsDisplayed passes once any of it shows.
     */
    private fun SemanticsNodeInteraction.assertIsWhollyDisplayed() = apply {
        val node = fetchSemanticsNode()
        assertEquals(node.size.toSize(), node.boundsInRoot.size)
    }

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
            .onNodeWithText("This requirement isn't in the requirements this badge or rank uses.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Requirement 2").assertDoesNotExist()
    }

    @Test
    fun unavailable_isAnnouncedWhenItReplacesLoading() {
        show(RequirementDetailUiState.Loading)

        composeTestRule.assertAnnouncedWhenShown(
            "This requirement isn't in the requirements this badge or rank uses."
        ) { uiState = RequirementDetailUiState.Unavailable }
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
    fun loadFailed_isAnnouncedWhenItReplacesLoading() {
        show(RequirementDetailUiState.Loading)

        composeTestRule.assertAnnouncedWhenShown(
            "Couldn't load your data. Try closing and reopening BlueCard."
        ) { uiState = RequirementDetailUiState.LoadFailed }
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

    @Test
    fun subRequirementWithTotals_showsThemInsteadOfHowManyRows() {
        val totals = listOf(
            TrackerTotal(BigDecimal("4.5"), ColumnTotal(6, "hour", "hours")),
            TrackerTotal(BigDecimal("2"), ColumnTotal(3, "conservation hour", "conservation hours"))
        )
        val child = RequirementItem(
            "2d",
            "Give six hours of service.",
            null,
            completed = false,
            markedByHand = true,
            TrackerCount(2, null, "projects", totals)
        )
        show(ready.copy(children = ready.children + child))

        row("Give six hours of service.")
            .assert(hasText("4.5 of 6 hours"))
            .assert(hasText("2 of 3 conservation hours"))
            .assert(hasText("2 projects").not())
    }

    /** Sub-requirements with these numbers, each summarized as "Summary of <number>.". */
    private fun withSubRequirements(numbers: List<String>) = ready.copy(
        children = numbers.map {
            RequirementItem(it, "Summary of $it.", null, completed = false, markedByHand = true)
        }
    )

    // Like Emergency Preparedness 1b's: too long for the box's minimum width, and not all as long
    // as each other.
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

    @Test
    fun longestNumbers_showInFullOnOneLine() {
        show(withSubRequirements(longestNumbers))

        assertShownInFullOnOneLine(longestNumbers)
    }

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
    fun logWithATotal_showsItUnderTheCount() {
        val total = TrackerTotal(BigDecimal("1.5"), ColumnTotal(1, "hour", "hours"))
        show(
            withLog.copy(
                tracker = withLog.tracker?.copy(
                    count = TrackerCount(2, null, "sessions", listOf(total))
                )
            )
        )

        val heading = composeTestRule.onNodeWithText("2 sessions").performScrollTo()
        val shown = composeTestRule.onNodeWithText("1.5 of 1 hour").performScrollTo()
            .assert(isHeading().not())
        assertTrue(
            shown.getUnclippedBoundsInRoot().top >= heading.getUnclippedBoundsInRoot().bottom
        )
        assertTrue(
            shown.getUnclippedBoundsInRoot().bottom <=
                row("Session 1").getUnclippedBoundsInRoot().top
        )
    }

    @Test
    fun logEntry_showsALineBreakInAValueAsASpace() {
        val values = listOf(
            // As saved before the field replaced pasted line breaks (#155).
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
        completedDate = LocalDate.of(2026, 4, 15),
        rowsCompletedDate = LocalDate.of(2026, 4, 12),
        tracker = withWeeks.tracker?.copy(
            count = TrackerCount(3, 3, "weeks"),
            rows = (1..3).map {
                TrackerRow(it, it.toLong(), listOf(TrackerValue(TrackerColumnType.NUMBER, "20")))
            }
        )
    )

    @Test
    fun fixedRowsAllFilledIn_areLabeledCompleted_withTheirDateButNoCheckbox() {
        show(weeksFilledIn)

        composeTestRule.onNodeWithText("Completed").assertIsDisplayed()
        composeTestRule.onNode(hasText("Completed") and isToggleable()).assertDoesNotExist()
        composeTestRule.onNodeWithText("Completed on Apr 15, 2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remove date").assertIsDisplayed()
    }

    /** [weeksFilledIn] with work of its own besides its rows, not done yet. */
    private val weeksAndOwnWork = weeksFilledIn.copy(
        requirement = weeksFilledIn.requirement.copy(
            completed = false,
            completesFromRows = false,
            ownWork = OwnWork("Compare the weeks.", false)
        ),
        completedDate = null,
        rowsCompletedDate = null
    )

    // Its rows get no date of their own: the own work's is the requirement's.
    @Test
    fun fixedRowsAndOwnWork_haveItsCheckboxAboveTheRows_andNoDateForTheRows() {
        show(weeksAndOwnWork)

        val checkbox = composeTestRule.onNode(hasText("Compare the weeks.") and isToggleable())
            .assertIsOff()
            .getUnclippedBoundsInRoot()
        assertTrue(checkbox.bottom <= row("Week 1").getUnclippedBoundsInRoot().top)
        composeTestRule.onNodeWithText("Completed").assertDoesNotExist()
        composeTestRule.onNodeWithText("date", substring = true).assertDoesNotExist()

        composeTestRule.onNode(hasText("Compare the weeks.") and isToggleable()).performClick()
        assertEquals(listOf(true), completedChanges)
    }

    @Test
    fun fixedRowsAndOwnWorkDone_areLabeledCompleted_onTheOwnWorksDate() {
        show(
            weeksAndOwnWork.copy(
                requirement = weeksAndOwnWork.requirement.copy(
                    completed = true,
                    ownWork = OwnWork("Compare the weeks.", true)
                ),
                completedDate = LocalDate.of(2026, 4, 15)
            )
        )

        composeTestRule.onNodeWithText("Completed").assertIsDisplayed()
        composeTestRule.onNode(hasText("Compare the weeks.") and isToggleable()).assertIsOn()
        composeTestRule.onNodeWithText("Completed on Apr 15, 2026").performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun fixedRowsAllFilledIn_changeDate_picksAnotherDay() {
        show(weeksFilledIn)

        composeTestRule.onNodeWithText("Change date").performClick()
        // It opens at the date shown, not the rows' date.
        pickerDay(
            "April 15, 2026"
        ).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        pickerDay("April 10, 2026").performClick()
        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals(listOf<LocalDate?>(LocalDate.of(2026, 4, 10)), dateChanges)
    }

    @Test
    fun fixedRowsAllFilledIn_removeDate_removesIt() {
        show(weeksFilledIn)

        composeTestRule.onNodeWithText("Remove date").performClick()

        assertEquals(listOf<LocalDate?>(null), dateChanges)
    }

    @Test
    fun fixedRowsAllFilledIn_withoutDate_offerToAddOne_openingAtTheRowsDate() {
        show(weeksFilledIn.copy(completedDate = null))

        composeTestRule.onNodeWithText("No completion date").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add date").performClick()
        pickerDay(
            "April 12, 2026"
        ).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals(listOf<LocalDate?>(LocalDate.of(2026, 4, 12)), dateChanges)
    }

    // As when a row was saved before database version 3, which recorded no date.
    @Test
    fun fixedRowsAllFilledIn_withoutDateOrRowsDate_addDateOpensAtToday() {
        show(weeksFilledIn.copy(completedDate = null, rowsCompletedDate = null))

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
    fun timeInRank_saysWhenTheScoutIsEligible_underTheSummary_aboveTheCheckbox() {
        show(withTimeInRank)

        val checkbox = completedCheckbox()
        val summary = composeTestRule.onNodeWithText("Be active for four months.")
        val line = composeTestRule
            .onNodeWithText("Eligible from Jan 1, 2027, 4 months after earning First Class")
            .assertIsDisplayed()
        assertTrue(
            line.getUnclippedBoundsInRoot().top >= summary.getUnclippedBoundsInRoot().bottom
        )
        assertTrue(
            line.getUnclippedBoundsInRoot().bottom <= checkbox.getUnclippedBoundsInRoot().top
        )
    }

    @Test
    fun timeInRank_whileTheRankBelowIsntEarned_saysSo() {
        show(
            withTimeInRank.copy(
                timeInRank = TimeInRank(4, "First Class", TimeInRank.Eligibility.RankBelowNotEarned)
            )
        )

        composeTestRule
            .onNodeWithText("Eligible 4 months after earning First Class, which isn't earned yet")
            .assertIsDisplayed()
    }

    @Test
    fun timeInRank_whileTheRankBelowHasNoDate_saysSo() {
        show(
            withTimeInRank.copy(
                timeInRank = TimeInRank(4, "First Class", TimeInRank.Eligibility.RankBelowHasNoDate)
            )
        )

        composeTestRule
            .onNodeWithText(
                "Eligible 4 months after earning First Class, which has no date it was earned on"
            )
            .assertIsDisplayed()
    }

    @Test
    fun timeInRank_ofOneMonth_saysMonth() {
        show(
            withTimeInRank.copy(
                timeInRank = TimeInRank(
                    1,
                    "First Class",
                    TimeInRank.Eligibility.From(LocalDate.of(2026, 10, 1))
                )
            )
        )

        composeTestRule
            .onNodeWithText("Eligible from Oct 1, 2026, 1 month after earning First Class")
            .assertIsDisplayed()
    }

    // The date is only a guide, so it stays once the scout checks the requirement off.
    @Test
    fun timeInRank_ofACompletedRequirement_staysWithTheCheckedBox() {
        show(
            withTimeInRank.copy(
                requirement = withTimeInRank.requirement.copy(completed = true),
                completedDate = LocalDate.of(2026, 11, 2)
            )
        )

        composeTestRule
            .onNodeWithText("Eligible from Jan 1, 2027, 4 months after earning First Class")
            .assertIsDisplayed()
        completedCheckbox().assertIsOn()
    }

    private fun badge(id: String, name: String, eagleRequired: Boolean = false) = MeritBadge(
        id = id,
        name = name,
        summary = "Our summary of $name.",
        officialUrl = "https://www.scouting.org/merit-badges/$id/",
        eagleRequired = eagleRequired,
        requirementVersions = emptyList()
    )

    /** Six merit badges, four of them Eagle-required, the scout has [completed] of. */
    private fun meritBadgeCredit(completed: Int, eagleRequired: Int) = MeritBadgeCredit(
        MeritBadgesNeeded(total = 6, eagleRequired = 4),
        completed,
        eagleRequired,
        if (completed >= 6 && eagleRequired >= 4) Completion(null) else null
    )

    /**
     * A rank's requirement that asks for six merit badges, four of them Eagle-required, as Star 3
     * does. The scout has four, three of them Eagle-required: Camping, with no date, Chess, and
     * Hiking and Swimming, of the same "one of" group, which both count.
     */
    private val withMeritBadges = leaf.copy(
        advancementName = "Star",
        requirement = RequirementItem(
            "3",
            "Earn six merit badges.",
            null,
            false,
            markedByHand = false,
            partlyCompleted = true,
            meritBadges = meritBadgeCredit(completed = 4, eagleRequired = 3)
        ),
        earnedBadges = listOf(
            EarnedBadge(badge("camping", "Camping", true), null, countsOnceAsEagleRequired = true),
            EarnedBadge(badge("chess", "Chess"), LocalDate.of(2026, 2, 1), false),
            EarnedBadge(badge("hiking", "Hiking", true), LocalDate.of(2026, 3, 1), true),
            EarnedBadge(badge("swimming", "Swimming", true), LocalDate.of(2026, 4, 15), false)
        )
    )

    /** [withMeritBadges] with [credit] instead. */
    private fun withMeritBadgeCredit(credit: MeritBadgeCredit) = withMeritBadges.copy(
        requirement = withMeritBadges.requirement.copy(
            completed = credit.completion != null,
            meritBadges = credit
        )
    )

    @Test
    fun subRequirementWithMeritBadges_showsHowManyCount() {
        val child = RequirementItem(
            "2d",
            "Earn six merit badges.",
            null,
            completed = false,
            markedByHand = false,
            meritBadges = meritBadgeCredit(completed = 1, eagleRequired = 0)
        )
        show(ready.copy(children = ready.children + child))

        row("Earn six merit badges.")
            .assert(hasText("1 of 6 merit badges"))
            .assert(hasText("0 of 4 Eagle-required"))
    }

    @Test
    fun meritBadges_showHowManyCountAndHowManyMoreAreNeeded_underTheirHeading() {
        show(withMeritBadges)

        val heading = composeTestRule.onNodeWithText("Merit badges").performScrollTo()
            .assert(isHeading())
        listOf(
            "4 of 6 merit badges",
            "3 of 4 Eagle-required",
            "Needs 2 more merit badges, 1 of them Eagle-required"
        ).forEach {
            val line = composeTestRule.onNodeWithText(
                it
            ).performScrollTo().assert(isHeading().not())
            assertTrue(
                line.getUnclippedBoundsInRoot().top >= heading.getUnclippedBoundsInRoot().bottom
            )
            assertTrue(
                line.getUnclippedBoundsInRoot().bottom <=
                    row("Camping").getUnclippedBoundsInRoot().top
            )
        }
    }

    // They're complete once the scout has enough badges, not by hand.
    @Test
    fun meritBadges_haveNoCheckbox() {
        show(withMeritBadges)

        composeTestRule.onNode(isToggleable()).assertDoesNotExist()
    }

    @Test
    fun meritBadges_listTheBadges_withTheirDates_andEachEagleRequiredOne() {
        show(withMeritBadges)

        row("Camping")
            .assert(hasText("Completed"))
            .assert(hasText("Eagle-required"))
        row("Chess")
            .assert(hasText("Completed on Feb 1, 2026"))
            .assert(hasText("Eagle-required").not())
        row("Hiking")
            .assert(hasText("Completed on Mar 1, 2026"))
            .assert(hasText("Eagle-required"))
        row("Swimming")
            .assert(hasText("Completed on Apr 15, 2026"))
            .assert(hasText("Eagle-required"))
    }

    // As for Eagle 3: of Hiking and Swimming, only Hiking, completed first, counts.
    @Test
    fun meritBadgesWhoseGroupsCountOnce_labelOnlyTheFirstBadgeOfAGroup() {
        show(withMeritBadgeCredit(MeritBadgeCredit(MeritBadgesNeeded(21, 13, true), 4, 2, null)))

        row("Camping").assert(hasText("Eagle-required"))
        row("Hiking").assert(hasText("Eagle-required"))
        row("Swimming").assert(hasText("Eagle-required").not())
    }

    @Test
    fun tappingABadge_opensIt() {
        show(withMeritBadges)

        row("Hiking").assertHasClickAction().performClick()

        assertEquals(listOf("hiking"), openedBadges)
    }

    @Test
    fun meritBadges_needed_whenAnyBadgeWillDo() {
        show(withMeritBadgeCredit(meritBadgeCredit(completed = 4, eagleRequired = 4)))

        composeTestRule.onNodeWithText("Needs 2 more merit badges").performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun meritBadges_needed_oneMore() {
        show(withMeritBadgeCredit(meritBadgeCredit(completed = 5, eagleRequired = 4)))

        composeTestRule.onNodeWithText("Needs 1 more merit badge").performScrollTo()
            .assertIsDisplayed()
    }

    // Seven badges, but only two Eagle-required.
    @Test
    fun meritBadges_needed_whenOnlyEagleRequiredBadgesWillDo() {
        show(withMeritBadgeCredit(meritBadgeCredit(completed = 7, eagleRequired = 2)))

        composeTestRule.onNodeWithText("7 of 6 merit badges").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Needs 2 more Eagle-required merit badges")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun meritBadges_withEnough_areCompleted_andNeedNoMore() {
        show(withMeritBadgeCredit(meritBadgeCredit(completed = 6, eagleRequired = 4)))

        // Under the summary, not on Camping's row, which has no date.
        composeTestRule.onNode(hasText("Completed") and hasClickAction().not())
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Needs", substring = true).assertDoesNotExist()
    }

    private fun hasClickLabel(label: String) = SemanticsMatcher("click label is \"$label\"") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    @Test
    fun meritBadges_withNoBadges_listNone() {
        show(
            withMeritBadgeCredit(meritBadgeCredit(completed = 0, eagleRequired = 0))
                .copy(earnedBadges = emptyList())
        )

        composeTestRule.onNodeWithText("0 of 6 merit badges").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Needs 6 more merit badges, 4 of them Eagle-required")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNode(hasClickLabel("open badge")).assertDoesNotExist()
    }

    @Test
    fun requirementWithoutMeritBadges_listsNoBadges() {
        show(leaf)

        composeTestRule.onNodeWithText("Merit badges").assertDoesNotExist()
    }

    @Test
    fun requirementWithoutTimeInRank_saysNothingAboutEligibility() {
        show(leaf)

        composeTestRule.onNodeWithText("Eligible", substring = true).assertDoesNotExist()
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

    // Only a tracker row's date buttons name their date: this page is about one date.
    @Test
    fun completedLeafWithDate_dateButtonsReadOnlyTheirText() {
        show(completedLeaf)

        listOf("Change date", "Remove date").forEach {
            composeTestRule.onNodeWithText(it)
                .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        }
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

    // A phone in landscape, where the picker is taller than the dialog: Material leaves room for
    // six weeks in every month. November 2025 fills them, with its first day alone in the first
    // week and its last day alone in the sixth.
    @Config(qualifiers = "w891dp-h411dp-land")
    @Test
    fun datePicker_onAShortWindow_scrollsToEveryDay() {
        show(completedLeaf.copy(completedDate = LocalDate.of(2025, 11, 12)))

        composeTestRule.onNodeWithText("Change date").performScrollTo().performClick()
        pickerDay("November 1, 2025").assertIsWhollyDisplayed()
        pickerDay("November 30, 2025").assertIsNotDisplayed()
        // performScrollTo would scroll only the months, which scroll sideways.
        composeTestRule.onNode(
            hasAnyAncestor(isDialog()) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        ).performTouchInput { swipeUp() }
        pickerDay("November 30, 2025").assertIsWhollyDisplayed().performClick()
        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals(listOf<LocalDate?>(LocalDate.of(2025, 11, 30)), dateChanges)
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

    // Seen on a phone: the page scrolled only far enough to show the cursor, leaving Save notes
    // under the field behind the keyboard (#178).
    @Config(qualifiers = SMALL_PHONE)
    @Test
    fun keyboardOpensForComment_commentAndSaveShowAboveIt() {
        show(withLog.copy(canClear = true))

        commentField().performClick()
        keyboard.open()

        keyboard.assertAbove(commentFieldWithoutScrolling().getUnclippedBoundsInRoot())
        keyboard.assertAbove(saveCommentButtonWithoutScrolling().getUnclippedBoundsInRoot())
    }

    @Test
    fun unchangedComment_cannotBeSaved() {
        show(leaf)

        saveCommentButton().assertIsNotEnabled()
    }

    @Test
    fun changedComment_isSaved() {
        show(leaf.copy(textChanged = true))

        saveCommentButton().assertIsEnabled().performClick()

        assertEquals(1, saves)
    }

    @Test
    fun back_withChangedComment_asksBeforeDiscardingIt() {
        show(completedLeaf.copy(textChanged = true))

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
    fun badgesRequirement_hasNoSignOffField() {
        show(leaf)

        composeTestRule.onNodeWithText("Signed off by").assertDoesNotExist()
    }

    @Test
    fun ranksRequirement_hasASignOffField_aboveTheNotes() {
        show(rankLeaf)

        signOffField().performTextInput("Mr. Rivera")

        assertEquals("Mr. Rivera", signedOffBy.text.toString())
        assertTrue(
            signOffField().getUnclippedBoundsInRoot().bottom <=
                commentField().getUnclippedBoundsInRoot().top
        )
    }

    @Test
    fun signOff_keyboardNext_movesToTheNotes() {
        show(rankLeaf)

        signOffField().assert(hasImeAction(ImeAction.Next)).performClick().performImeAction()

        commentField().assertIsFocused()
    }

    // On a rank's requirement, Next from the sign-off is how the scout reaches the notes.
    @Config(qualifiers = SMALL_PHONE)
    @Test
    fun nextIntoComment_withKeyboardOpen_commentAndSaveShowAboveIt() {
        show(rankLeaf.copy(canClear = true))
        signOffField().performClick()
        keyboard.open()

        signOffField().performImeAction()

        commentFieldWithoutScrolling().assertIsFocused()
        keyboard.assertAbove(commentFieldWithoutScrolling().getUnclippedBoundsInRoot())
        keyboard.assertAbove(composeTestRule.onNodeWithText("Save").getUnclippedBoundsInRoot())
    }

    @Test
    fun signOff_isOneLine_withAPastedLineBreakAsASpace_andTrimsTextPast100Characters() {
        show(rankLeaf)

        signOffField().performTextInput("Mr. Rivera\nScoutmaster")
        assertEquals("Mr. Rivera Scoutmaster", signedOffBy.text.toString())

        signOffField().performTextInput("x".repeat(100))
        assertEquals(100, signedOffBy.text.length)
        signOffField().assert(SemanticsMatcher.expectValue(SemanticsProperties.MaxTextLength, 100))
    }

    // One button saves the sign-off and the notes, so it isn't "Save notes".
    @Test
    fun ranksRequirement_changed_isSavedWithSave() {
        show(rankLeaf.copy(textChanged = true))

        composeTestRule.onNodeWithText("Save notes").assertDoesNotExist()
        composeTestRule.onNodeWithText("Save").performScrollTo().assertIsEnabled().performClick()

        assertEquals(1, saves)
    }

    @Test
    fun ranksRequirement_unchanged_cannotBeSaved() {
        show(rankLeaf)

        composeTestRule.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun back_withChangesOnARanksRequirement_saysTheSignOffAndNotesArentSaved() {
        show(rankLeaf.copy(textChanged = true))

        back.press(composeTestRule)
        composeTestRule.onNodeWithText("Your changes to the sign-off and notes haven't been saved.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Discard").performClick()

        assertEquals(1, discards)
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
        show(completedLeaf.copy(textChanged = true, canClear = true))

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
        show(ready.copy(textChanged = true, canClear = true))

        clearButton().performClick()

        composeTestRule
            .onNodeWithText(
                "What you recorded for it and the requirements under it will be removed, " +
                    "along with unsaved changes to its notes."
            )
            .assertIsDisplayed()
    }

    @Test
    fun clear_onARanksRequirement_withUnsavedChanges_saysTheSignOffAndNotesAreDiscarded() {
        show(rankLeaf.copy(textChanged = true, canClear = true))

        clearButton().performClick()

        composeTestRule
            .onNodeWithText(
                "What you recorded for it will be removed, along with unsaved changes to its " +
                    "sign-off and notes."
            )
            .assertIsDisplayed()
    }

    @Test
    fun clear_onARanksRequirementWithSubRequirements_withUnsavedChanges_saysAll() {
        show(ready.copy(hasSignOffField = true, textChanged = true, canClear = true))

        clearButton().performClick()

        composeTestRule
            .onNodeWithText(
                "What you recorded for it and the requirements under it will be removed, " +
                    "along with unsaved changes to its sign-off and notes."
            )
            .assertIsDisplayed()
    }

    @Test
    fun clear_ofARequirementRanksCountOn_namesTheRanksThatWontCountAsEarned() {
        show(
            ready.copy(
                hasSignOffField = true,
                textChanged = true,
                canClear = true,
                unearnedByClear = listOf("Tenderfoot", "Second Class")
            )
        )

        clearButton().performClick()

        composeTestRule
            .onNodeWithText(
                "What you recorded for it and the requirements under it will be removed, " +
                    "along with unsaved changes to its sign-off and notes. Tenderfoot and " +
                    "Second Class will no longer count as earned."
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
