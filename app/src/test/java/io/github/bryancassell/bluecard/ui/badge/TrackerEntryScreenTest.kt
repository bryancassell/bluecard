package io.github.bryancassell.bluecard.ui.badge

import android.graphics.Rect
import android.view.View
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.testing.paragraphDirection
import io.github.bryancassell.bluecard.ui.SaveFailure
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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
    private val saveFailuresShown = mutableListOf<SaveFailure>()

    /** The page's view, which the keyboard's insets are sent to. */
    private lateinit var view: View

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
            view = LocalView.current
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

    /** The field for [label], scrolled into view. */
    private fun field(label: String) = fieldWithoutScrolling(label).performScrollTo()

    /** The field for [label], wherever the page has it. */
    private fun fieldWithoutScrolling(label: String) =
        composeTestRule.onNode(hasSetTextAction() and hasText(label))

    private fun button(text: String) = composeTestRule.onNodeWithText(text).performScrollTo()

    /**
     * Opens the keyboard over the bottom of the page, as the system does once a field has focus:
     * after the page has handled the focus change.
     */
    private fun openKeyboard() = showKeyboard(KEYBOARD_HEIGHT)

    /** Closes the keyboard, as the scout does with Back while a field keeps focus. */
    private fun closeKeyboard() = showKeyboard(0.dp)

    private fun showKeyboard(height: Dp) {
        val pixels = with(composeTestRule.density) { height.roundToPx() }
        composeTestRule.runOnIdle {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, pixels))
                .setVisible(WindowInsetsCompat.Type.ime(), pixels > 0)
                .build()
            ViewCompat.dispatchApplyWindowInsets(view, insets)
        }
    }

    /**
     * Checks that [bounds] are on the page above the keyboard. Unclipped bounds are needed for
     * this: the page clips what's behind the keyboard.
     */
    private fun assertAboveKeyboard(bounds: DpRect) {
        val keyboardTop =
            composeTestRule.onRoot().getUnclippedBoundsInRoot().bottom - KEYBOARD_HEIGHT
        assertTrue(
            "$bounds isn't between the top of the page and the keyboard at $keyboardTop",
            bounds.top >= 0.dp && bounds.bottom <= keyboardTop
        )
    }

    /** Where the focused field's cursor is: the view reports it as its focused area. */
    private fun cursorBounds(): DpRect {
        val rect = Rect()
        composeTestRule.runOnIdle { view.getFocusedRect(rect) }
        return with(composeTestRule.density) {
            DpRect(rect.left.toDp(), rect.top.toDp(), rect.right.toDp(), rect.bottom.toDp())
        }
    }

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

    // Seen on a phone: the page scrolled only far enough to show the cursor, leaving Save under
    // the field behind the keyboard (#172).
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = SMALL_PHONE)
    @Test
    fun keyboardOpensForLastField_fieldAndSaveShowAboveIt() {
        show(newEntry)

        fieldWithoutScrolling("Notes").performClick()
        openKeyboard()

        assertAboveKeyboard(fieldWithoutScrolling("Notes").getUnclippedBoundsInRoot())
        assertAboveKeyboard(composeTestRule.onNodeWithText("Save").getUnclippedBoundsInRoot())
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = SMALL_PHONE)
    @Test
    fun lastFieldGrowingAsScoutTypes_saveStaysAboveKeyboard() {
        show(newEntry)
        fieldWithoutScrolling("Notes").performClick()
        openKeyboard()

        fieldWithoutScrolling("Notes").performTextInput("Ran 2 miles\nSwam\nStretched\nRested\n")

        assertAboveKeyboard(composeTestRule.onNodeWithText("Save").getUnclippedBoundsInRoot())
    }

    // Higher fields scroll into view as before, as far as their cursor, without pulling the page
    // down to Save.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = SMALL_PHONE)
    @Test
    fun keyboardOpensForHigherField_itsCursorShowsAboveIt() {
        show(newEntry)

        fieldWithoutScrolling("Activity").performClick()
        openKeyboard()

        assertAboveKeyboard(cursorBounds())
    }

    // Too tall to show with Save above the keyboard, so the page keeps the cursor in view as the
    // keyboard opens, as for any other field.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = SMALL_PHONE)
    @Test
    fun keyboardOpensForLastFieldTooTallToShowWithSave_itsCursorShowsAboveIt() {
        // With the cursor at the end, as after typing it.
        val notes = TextFieldState((1..20).joinToString("\n") { "Line $it" })
        show(newEntry, fields + ("notes" to notes))

        fieldWithoutScrolling("Notes").requestFocus()
        openKeyboard()

        assertAboveKeyboard(cursorBounds())
    }

    // With the last field still focused, the scout scrolled up to check an earlier one. Closing
    // the keyboard leaves the page there, rather than pulling it back down to Save.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = SMALL_PHONE)
    @Test
    fun keyboardClosingAfterScrollingAway_leavesThePageWhereItIs() {
        // More fields than fit on the page, with Notes still last.
        val more = listOf("Route", "Weather", "Partner", "Goal").map {
            TrackerColumn(it.lowercase(), it, TrackerColumnType.TEXT)
        }
        show(
            newEntry.copy(columns = columns.dropLast(1) + more + columns.last()),
            fields + more.associate { it.id to TextFieldState() }
        )
        field("Notes").performClick()
        openKeyboard()
        val heading = composeTestRule.onNodeWithText("Session 3").performScrollTo()
        val scrolledTo = heading.getUnclippedBoundsInRoot()

        closeKeyboard()

        assertEquals(scrolledTo, heading.getUnclippedBoundsInRoot())
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

    private companion object {
        /** A small phone's screen, less its system bars. */
        const val SMALL_PHONE = "w360dp-h560dp"

        /** About as tall as a phone's keyboard. */
        val KEYBOARD_HEIGHT = 300.dp
    }
}
