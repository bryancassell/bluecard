package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.testing.BackPresses
import io.github.bryancassell.bluecard.testing.paragraphDirection
import io.github.bryancassell.bluecard.ui.SaveFailure
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** One test per UI state and interaction, with fixed UI state. */
@RunWith(AndroidJUnit4::class)
class TrackerEntryScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /** What the page reads as today when the picker opens, which a test can move on. */
    private var today = LocalDate.of(2026, 5, 20)

    private val columns = listOf(
        TrackerColumn("date", "Date", TrackerColumnType.DATE),
        TrackerColumn("activity", "Activity", TrackerColumnType.TEXT),
        TrackerColumn("minutes", "Minutes", TrackerColumnType.NUMBER),
        TrackerColumn("notes", "Notes", TrackerColumnType.MULTILINE_TEXT)
    )
    private val fields = columns.associate { it.id to TextFieldState() }

    private val dateChanges = mutableListOf<Pair<String, LocalDate?>>()
    private var saves = 0
    private var deletes = 0
    private var closes = 0
    private val back = BackPresses()
    private val saveFailuresShown = mutableListOf<SaveFailure>()

    /** A new entry, with nothing in it yet. */
    private val newEntry = TrackerEntryUiState.Ready(
        badgeName = "Personal Fitness",
        requirementNumber = "7a",
        rowTitle = "Session",
        rowNumber = 3,
        rowLabel = "session",
        columns = columns,
        dates = emptyMap(),
        canSave = false,
        changed = false,
        hasSavedEntry = false,
        canDelete = false
    )

    /** A saved entry, with a date. */
    private val savedEntry = newEntry.copy(
        rowNumber = 2,
        dates = mapOf("date" to LocalDate.of(2026, 4, 15)),
        hasSavedEntry = true,
        canDelete = true
    )

    private fun show(
        uiState: TrackerEntryUiState,
        fields: Map<String, TextFieldState> = this.fields
    ) {
        composeTestRule.setContent {
            back.Content {
                TrackerEntryScreen(
                    uiState = uiState,
                    fields = fields,
                    onDateChange = { columnId, date -> dateChanges += columnId to date },
                    today = { today },
                    onSave = { saves++ },
                    onDelete = { deletes++ },
                    onClose = { closes++ },
                    onSaveFailureShown = { saveFailuresShown += it }
                )
            }
        }
    }

    private fun field(label: String) =
        composeTestRule.onNode(hasSetTextAction() and hasText(label)).performScrollTo()

    private fun button(text: String) = composeTestRule.onNodeWithText(text).performScrollTo()

    // A day in the date picker, which reads each day as its full date.
    private fun pickerDay(date: String) =
        composeTestRule.onNode(hasText(date, substring = true) and hasClickAction())

    @Test
    fun loading_showsProgressOnly() {
        show(TrackerEntryUiState.Loading)

        composeTestRule
            .onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo.Indeterminate
                )
            )
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Session", substring = true).assertDoesNotExist()
    }

    @Test
    fun loadFailed_showsMessageOnly() {
        show(TrackerEntryUiState.LoadFailed)

        composeTestRule.onNodeWithText(
            "Couldn't load your data. Try closing and reopening BlueCard."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Session", substring = true).assertDoesNotExist()
    }

    @Test
    fun unavailable_showsMessageOnly() {
        show(TrackerEntryUiState.Unavailable)

        composeTestRule.onNodeWithText("This entry isn't in the tracker anymore.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Save").assertDoesNotExist()
    }

    @Test
    fun ready_showsWhichRowItIs() {
        show(newEntry)

        composeTestRule.onNodeWithText("Personal Fitness").assertIsDisplayed()
        composeTestRule.onNodeWithText("Requirement 7a").assertIsDisplayed()
        composeTestRule.onNodeWithText("Session 3").assert(isHeading()).assertIsDisplayed()
    }

    @Test
    fun ready_hasAFieldForEachColumn_inOrder() {
        show(newEntry)

        composeTestRule.onNodeWithText("Date").assertIsDisplayed()
        composeTestRule.onNodeWithText("No date").assertIsDisplayed()
        field("Activity").assertIsDisplayed()
        field("Minutes").assertIsDisplayed()
        field("Notes").assertIsDisplayed()
    }

    @Test
    fun textField_isEditable() {
        show(newEntry)

        field("Activity").performTextInput("Swimming")

        assertEquals("Swimming", fields.getValue("activity").text.toString())
    }

    @Test
    fun textTypedInPersian_readsRightToLeft() {
        show(newEntry)

        field("Activity").performTextInput("شنا کردم.")

        assertEquals(ResolvedTextDirection.Rtl, field("Activity").paragraphDirection())
    }

    @Test
    fun textField_trimsTextPast500Characters() {
        show(newEntry)
        field("Activity").performTextInput("a".repeat(495))

        // As when pasting.
        field("Activity").performTextInput("b".repeat(10))

        assertEquals("a".repeat(495) + "b".repeat(5), fields.getValue("activity").text.toString())
    }

    @Test
    fun textField_replacesALineBreakWithASpace() {
        show(newEntry)

        // As when pasting.
        field("Activity").performTextInput("Ran\nthen swam")

        assertEquals("Ran then swam", fields.getValue("activity").text.toString())
    }

    @Test
    fun multilineTextField_keepsALineBreak() {
        show(newEntry)

        field("Notes").performTextInput("3 sets of 10\nFelt good")

        assertEquals("3 sets of 10\nFelt good", fields.getValue("notes").text.toString())
    }

    @Test
    fun multilineTextField_trimsTextPast2000Characters() {
        show(newEntry)
        field("Notes").performTextInput("a".repeat(1995))

        // As when pasting.
        field("Notes").performTextInput("b\n".repeat(5))

        assertEquals("a".repeat(1995) + "b\nb\nb", fields.getValue("notes").text.toString())
    }

    // Drawn like the requirement notes field, three lines tall before the scout types.
    @Test
    fun multilineTextField_isTallerThanATextField() {
        show(newEntry)

        val text = field("Activity").fetchSemanticsNode().size.height
        val multiline = field("Notes").fetchSemanticsNode().size.height
        assertTrue(multiline > text)
    }

    @Test
    fun numberField_takesOnlyANumber() {
        show(newEntry)

        field("Minutes").performTextInput("30")
        field("Minutes").performTextInput(" min")

        assertEquals("30", fields.getValue("minutes").text.toString())
    }

    @Test
    fun noDate_offersToAddOne_openingAtToday() {
        show(newEntry)

        button("Add date").performClick()
        pickerDay(
            "May 20, 2026"
        ).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        pickerDay("May 21, 2026").assertIsNotEnabled()
        pickerDay("May 11, 2026").performClick()
        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals(
            listOf<Pair<String, LocalDate?>>("date" to LocalDate.of(2026, 5, 11)),
            dateChanges
        )
    }

    @Test
    fun addDate_onAPageOpenPastMidnight_opensAtTheNewDay() {
        show(newEntry)
        today = LocalDate.of(2026, 5, 21)

        button("Add date").performClick()
        pickerDay("May 22, 2026").assertIsNotEnabled()
        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals(
            listOf<Pair<String, LocalDate?>>("date" to LocalDate.of(2026, 5, 21)),
            dateChanges
        )
    }

    @Test
    fun date_showsWithChangeAndRemove() {
        show(savedEntry)

        composeTestRule.onNodeWithText("Apr 15, 2026").assertIsDisplayed()
        button("Change date").performClick()
        pickerDay(
            "April 15, 2026"
        ).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        composeTestRule.onNodeWithText("Cancel").performClick()
        button("Remove date").performClick()

        assertEquals(listOf<Pair<String, LocalDate?>>("date" to null), dateChanges)
    }

    @Test
    fun dateButtons_sayWhichColumnTheySet() {
        val dateColumns = listOf(
            TrackerColumn("start", "Start", TrackerColumnType.DATE),
            TrackerColumn("end", "End", TrackerColumnType.DATE)
        )
        show(
            newEntry.copy(columns = dateColumns, dates = mapOf("end" to LocalDate.of(2026, 4, 15))),
            fields = dateColumns.associate { it.id to TextFieldState() }
        )

        composeTestRule.onNodeWithContentDescription("Start: Add date").assertHasClickAction()
        composeTestRule.onNodeWithContentDescription("End: Change date").assertHasClickAction()
        composeTestRule.onNodeWithContentDescription("End: Remove date").performClick()

        assertEquals(listOf<Pair<String, LocalDate?>>("end" to null), dateChanges)
    }

    @Test
    fun nothingToSave_cannotBeSaved() {
        show(newEntry)

        button("Save").assertIsNotEnabled()
    }

    @Test
    fun save_savesIt() {
        show(newEntry.copy(canSave = true))

        button("Save").assertIsEnabled().performClick()

        assertEquals(1, saves)
    }

    @Test
    fun newEntry_hasNoDeleteButton() {
        show(newEntry)

        composeTestRule.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test
    fun delete_asksFirst_thenDeletes() {
        show(savedEntry)

        button("Delete").performClick()
        composeTestRule.onNodeWithText("Delete this session?").assertIsDisplayed()
        assertEquals(0, deletes)
        composeTestRule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()

        assertEquals(1, deletes)
        composeTestRule.onNodeWithText("Delete this session?").assertDoesNotExist()
    }

    @Test
    fun whileSaving_deleteIsShownButCantBeUsed() {
        show(savedEntry.copy(canDelete = false))

        button("Delete").assertIsNotEnabled()
    }

    @Test
    fun delete_cancel_deletesNothing() {
        show(savedEntry)

        button("Delete").performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()

        assertEquals(0, deletes)
        composeTestRule.onNodeWithText("Delete this session?").assertDoesNotExist()
    }

    @Test
    fun back_withChangesThatCantBeSaved_asksBeforeDiscardingThem() {
        // As with every field of a saved entry emptied, which Delete removes instead.
        show(savedEntry.copy(canSave = false, changed = true))

        back.press(composeTestRule)
        composeTestRule.onNodeWithText("Discard changes?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Discard").performClick()

        assertEquals(1, closes)
        assertEquals(0, deletes)
        assertEquals(0, back.closes)
    }

    @Test
    fun back_withoutChanges_closesThePage() {
        show(savedEntry)

        back.press(composeTestRule)

        assertEquals(1, back.closes)
        assertEquals(0, closes)
        composeTestRule.onNodeWithText("Discard changes?").assertDoesNotExist()
    }

    @Test
    fun done_closesThePage() {
        show(savedEntry.copy(done = true))

        composeTestRule.waitForIdle()

        assertEquals(1, closes)
    }

    @Test
    fun notDone_staysOpen() {
        show(savedEntry)

        composeTestRule.waitForIdle()

        assertEquals(0, closes)
    }

    @Test
    fun saveFailed_showsMessage_thenReportsItShown() {
        val failure = SaveFailure()
        show(newEntry.copy(saveFailure = failure))

        composeTestRule.onNodeWithText("Couldn't save. Try again.").assertIsDisplayed()
        assertEquals(emptyList<SaveFailure>(), saveFailuresShown)

        // A short snackbar shows for 4 seconds.
        composeTestRule.mainClock.advanceTimeBy(5_000)

        composeTestRule.onNodeWithText("Couldn't save. Try again.").assertDoesNotExist()
        assertEquals(listOf(failure), saveFailuresShown)
    }
}
