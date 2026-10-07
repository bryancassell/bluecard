package io.github.bryancassell.bluecard.ui.badge

import android.graphics.Insets
import android.graphics.Rect
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.testing.BackPresses
import io.github.bryancassell.bluecard.testing.assertAnnouncedWhenShown
import io.github.bryancassell.bluecard.testing.paragraphDirection
import io.github.bryancassell.bluecard.ui.TaskFailure
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** One test per UI state and interaction, with fixed UI state. */
@RunWith(AndroidJUnit4::class)
class TrackerEntryScreenTest {
    // Shows the page in touch mode, as on a phone, where buttons can't take focus, so the
    // keyboard's Next skips a date's buttons. Robolectric reads it as the page's window opens.
    @get:Rule(order = 0)
    val touchMode = object : ExternalResource() {
        override fun before() {
            InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
        }
    }

    @get:Rule(order = 1)
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
    private val saveFailuresShown = mutableListOf<TaskFailure>()

    /** Records what the page asks of the on-screen keyboard. */
    private val keyboard = object : SoftwareKeyboardController {
        var hides = 0

        override fun show() {}

        override fun hide() {
            hides++
        }
    }

    /** The page's view, which the keyboard's insets are sent to. */
    private lateinit var view: View

    /** A new entry, with nothing in it yet. */
    private val newEntry = TrackerEntryUiState.Ready(
        advancementName = "Personal Fitness",
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

    /** The UI state shown, which a test can change after [show]. */
    private var uiState by mutableStateOf<TrackerEntryUiState>(TrackerEntryUiState.Loading)

    private fun show(
        state: TrackerEntryUiState,
        fields: Map<String, TextFieldState> = this.fields
    ) {
        uiState = state
        composeTestRule.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
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
    private fun openKeyboard() = moveKeyboard(from = 0.dp, to = KEYBOARD_HEIGHT)

    /** Closes the keyboard, as the scout does with Back while a field keeps focus. */
    private fun closeKeyboard() = moveKeyboard(from = KEYBOARD_HEIGHT, to = 0.dp)

    /**
     * Moves the keyboard's top edge as the system does: it sends the page its final insets, then
     * the insets of each frame of the animation.
     */
    private fun moveKeyboard(from: Dp, to: Dp) {
        val (start, end) = with(composeTestRule.density) { from.roundToPx() to to.roundToPx() }
        val animation = WindowInsetsAnimation(WindowInsets.Type.ime(), null, 250)
        val bounds = WindowInsetsAnimation.Bounds(
            Insets.NONE,
            Insets.of(0, 0, 0, maxOf(start, end))
        )
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.runOnUiThread {
            view.dispatchWindowInsetsAnimationPrepare(animation)
            view.dispatchApplyWindowInsets(keyboardInsets(end))
            view.dispatchWindowInsetsAnimationStart(animation, bounds)
        }
        for (frame in 1..KEYBOARD_FRAMES) {
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.runOnUiThread {
                animation.fraction = frame.toFloat() / KEYBOARD_FRAMES
                val height = start + (end - start) * frame / KEYBOARD_FRAMES
                view.dispatchWindowInsetsAnimationProgress(
                    keyboardInsets(height),
                    listOf(animation)
                )
            }
        }
        composeTestRule.runOnUiThread { view.dispatchWindowInsetsAnimationEnd(animation) }
        composeTestRule.mainClock.autoAdvance = true
    }

    private fun keyboardInsets(height: Int): WindowInsets = WindowInsets.Builder()
        .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, height))
        .setVisible(WindowInsets.Type.ime(), height > 0)
        .build()

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
    fun loadFailed_isAnnouncedWhenItReplacesLoading() {
        show(TrackerEntryUiState.Loading)

        composeTestRule.assertAnnouncedWhenShown(
            "Couldn't load your data. Try closing and reopening BlueCard."
        ) { uiState = TrackerEntryUiState.LoadFailed }
    }

    @Test
    fun unavailable_showsMessageOnly() {
        show(TrackerEntryUiState.Unavailable)

        composeTestRule.onNodeWithText("This entry isn't in the tracker anymore.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Save").assertDoesNotExist()
    }

    @Test
    fun unavailable_isAnnouncedWhenItReplacesLoading() {
        show(TrackerEntryUiState.Loading)

        composeTestRule.assertAnnouncedWhenShown(
            "This entry isn't in the tracker anymore."
        ) { uiState = TrackerEntryUiState.Unavailable }
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
    fun textField_asksForSentenceCapitalizationWithNext() {
        show(newEntry)
        field("Activity").performClick()

        val editorInfo = EditorInfo()
        composeTestRule.runOnIdle { view.onCreateInputConnection(editorInfo) }

        assertEquals(
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
            editorInfo.inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        )
        assertEquals(
            EditorInfo.IME_ACTION_NEXT,
            editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
        )
    }

    @Test
    fun numberField_asksForTheDecimalKeyboardWithNext() {
        show(newEntry)
        field("Minutes").performClick()

        val editorInfo = EditorInfo()
        composeTestRule.runOnIdle { view.onCreateInputConnection(editorInfo) }

        assertEquals(
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            editorInfo.inputType
        )
        assertEquals(
            EditorInfo.IME_ACTION_NEXT,
            editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
        )
    }

    @Test
    fun next_movesToTheNextField() {
        show(newEntry)

        field("Activity").assert(hasImeAction(ImeAction.Next)).performClick().performImeAction()
        field("Minutes").assertIsFocused().assert(hasImeAction(ImeAction.Next))
            .performImeAction()

        field("Notes").assertIsFocused()
    }

    // Its keyboard's Enter starts a new line.
    @Test
    fun multilineTextField_hasNoNextOrDone() {
        show(newEntry)

        field("Notes").assert(hasImeAction(ImeAction.Default))
    }

    // The scout taps a date's button when they're ready to pick the date.
    @Test
    fun next_skipsADate() {
        val columns = listOf(
            TrackerColumn("mammal", "Mammal", TrackerColumnType.TEXT),
            TrackerColumn("date", "Date", TrackerColumnType.DATE),
            TrackerColumn("time", "Time of day", TrackerColumnType.TEXT)
        )
        show(
            newEntry.copy(columns = columns),
            fields = columns.associate { it.id to TextFieldState() }
        )

        field("Mammal").performClick().performImeAction()

        field("Time of day").assertIsFocused()
    }

    @Test
    fun doneKey_onTheLastField_closesTheKeyboard() {
        val columns = listOf(
            TrackerColumn("species", "Species", TrackerColumnType.TEXT),
            TrackerColumn("count", "Count", TrackerColumnType.NUMBER)
        )
        show(
            newEntry.copy(columns = columns),
            fields = columns.associate { it.id to TextFieldState() }
        )

        field("Count").assert(hasImeAction(ImeAction.Done)).performClick().performImeAction()

        assertEquals(1, keyboard.hides)
    }

    // A field above a last date has no text field to move to.
    @Test
    fun doneKey_onTheLastFieldAboveADate_closesTheKeyboard() {
        val columns = listOf(
            TrackerColumn("species", "Species", TrackerColumnType.TEXT),
            TrackerColumn("date", "Date", TrackerColumnType.DATE)
        )
        show(
            newEntry.copy(columns = columns),
            fields = columns.associate { it.id to TextFieldState() }
        )

        field("Species").assert(hasImeAction(ImeAction.Done)).performClick().performImeAction()

        assertEquals(1, keyboard.hides)
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
    fun nextIntoLastField_withKeyboardOpen_fieldAndSaveShowAboveIt() {
        show(newEntry)
        fieldWithoutScrolling("Minutes").performClick()
        openKeyboard()

        fieldWithoutScrolling("Minutes").performImeAction()

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
        val failure = TaskFailure()
        show(newEntry.copy(saveFailure = failure))

        composeTestRule.onNodeWithText("Couldn't save. Try again.").assertIsDisplayed()
        assertEquals(emptyList<TaskFailure>(), saveFailuresShown)

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

        /** About how many frames a keyboard takes to open or close. */
        const val KEYBOARD_FRAMES = 15
    }
}
