package io.github.bryancassell.bluecard.ui.data

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.ViewConfiguration
import androidx.activity.ComponentDialog
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode.Companion.Polite
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.backup.Backup
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.testing.AccessibilityChecks
import io.github.bryancassell.bluecard.testing.assertButtonReadOnceAs
import io.github.bryancassell.bluecard.ui.data.DataManagementMessage.Kind
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DataManagementScreenTest {
    @get:Rule(order = 0)
    val composeTestRule = createComposeRule()

    @get:Rule(order = 1)
    val accessibilityChecks = AccessibilityChecks(composeTestRule)

    private var profileEdits = 0
    private val exported = mutableListOf<Uri>()
    private val read = mutableListOf<Uri>()
    private var merges = 0
    private var replaces = 0
    private var importsCancelled = 0
    private val profileChoices = mutableListOf<Boolean>()
    private val progressChoices = mutableListOf<Pair<String, Boolean>>()
    private var mergesConfirmed = 0
    private var clears = 0
    private val messagesShown = mutableListOf<DataManagementMessage>()

    /** The intents of the activities launched for a result: the file picker's. */
    private val launchedForResult = mutableListOf<Intent>()

    /** What the file picker gives back, or throws: the document chosen, or null for none. */
    private var pickFile: () -> Uri? = { file }
    private val file = Uri.parse("content://documents/document/export")

    /**
     * Stands in for the activity's result registry, so the file picker gives its result
     * straight away, as the testing guide shows:
     * https://developer.android.com/training/basics/intents/result#test
     */
    private val resultRegistryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?
            ) {
                launchedForResult += contract.createIntent(
                    ApplicationProvider.getApplicationContext(),
                    input
                )
                dispatchResult(requestCode, pickFile())
            }
        }
    }

    private val ready = DataManagementUiState()
    private val started = ready.copy(canClear = true)

    /** The date the screen reads when Export is tapped. */
    private var today = LocalDate.of(2026, 10, 1)
    private val backup = Backup(Profile("Sam Scout", "Crew 7"), emptyList())

    /** The UI state shown, which a test can change after [show]. */
    private var uiState by mutableStateOf(ready)

    private fun show(state: DataManagementUiState = ready) {
        uiState = state
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides resultRegistryOwner
            ) {
                DataManagementScreen(
                    uiState = uiState,
                    today = { today },
                    onEditProfile = { profileEdits++ },
                    onExport = { exported += it },
                    onImport = { read += it },
                    onMerge = { merges++ },
                    onReplace = { replaces++ },
                    onChooseProfile = { profileChoices += it },
                    onChooseProgress = { id, fromFile -> progressChoices += id to fromFile },
                    onConfirmMerge = { mergesConfirmed++ },
                    onCancelImport = { importsCancelled++ },
                    onClearAll = { clears++ },
                    onMessageShown = { messagesShown += it }
                )
            }
        }
    }

    private fun editProfileButton() = composeTestRule.onNodeWithText("Edit")

    private fun exportButton() = composeTestRule.onNodeWithText("Export")

    private fun importButton() = composeTestRule.onNodeWithText("Import")

    private fun clearButton() = composeTestRule.onNodeWithText("Clear all").performScrollTo()

    @Test
    fun showsWhatEachSectionDoes_underHeadings() {
        show(started)

        for (heading in listOf(
            "Data management",
            "Name and unit",
            "Export data",
            "Import data",
            "Clear all progress"
        )) {
            composeTestRule.onNode(hasTextAndHeading(heading)).performScrollTo().assertIsDisplayed()
        }
        composeTestRule
            .onNodeWithText("Change your name or unit number.")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Save your name, unit number and all your progress to a file.")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "Merge a file you exported with what's on this phone, or replace everything " +
                    "with it."
            )
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "Remove the progress on every badge and rank, including counselors. Your name " +
                    "and unit number stay."
            )
            .performScrollTo()
            .assertIsDisplayed()
        editProfileButton().assertIsEnabled()
        exportButton().assertIsEnabled()
        importButton().assertIsEnabled()
        clearButton().assertIsEnabled()
    }

    private fun hasTextAndHeading(text: String) = hasText(text) and isHeading()

    @Test
    fun edit_opensThePageForNameAndUnit() {
        show()

        editProfileButton().performClick()

        assertEquals(1, profileEdits)
    }

    // Moving from control to control, a screen reader user doesn't hear the heading above it.
    // What they hear starts with "Edit", as WCAG 2.5.3 recommends for voice control users.
    @Test
    fun edit_tellsScreenReadersWhatItEdits() {
        show()

        composeTestRule.assertButtonReadOnceAs("Edit", "Edit name and unit")
    }

    // The file picker takes a moment to cover BlueCard. A tap on Edit that reached it then would
    // open the page under the picker.
    @Test
    fun edit_rightAfterExport_isIgnored() {
        show()
        composeTestRule.mainClock.autoAdvance = false
        val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()

        exportButton().performClick()
        editProfileButton().performClick()
        composeTestRule.mainClock.advanceTimeBy(doubleTapTimeout / 2)
        assertEquals(0, profileEdits)

        composeTestRule.mainClock.advanceTimeBy(doubleTapTimeout)
        editProfileButton().performClick()
        assertEquals(1, profileEdits)
        assertEquals(listOf(file), exported)
    }

    @Test
    fun export_asksWhereToSaveIt_andExportsThere() {
        show()

        exportButton().performClick()

        val picker = launchedForResult.single()
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, picker.action)
        assertEquals("application/json", picker.type)
        assertEquals("BlueCard export 2026-10-01.json", picker.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals(listOf(file), exported)
    }

    // A screen left open past midnight suggests the new day.
    @Test
    fun export_namesTheFileWithTheDayItsTapped() {
        show()
        today = LocalDate.of(2026, 10, 2)

        exportButton().performClick()

        assertEquals(
            "BlueCard export 2026-10-02.json",
            launchedForResult.single().getStringExtra(Intent.EXTRA_TITLE)
        )
    }

    @Test
    fun export_leavingTheFilePickerWithoutSaving_exportsNothing() {
        pickFile = { null }
        show()

        exportButton().performClick()

        assertEquals(1, launchedForResult.size)
        assertEquals(emptyList<Uri>(), exported)
    }

    @Test
    fun export_withNoFilePicker_showsMessage() {
        pickFile = { throw ActivityNotFoundException() }
        show()

        exportButton().performClick()

        assertEquals("No app on this phone can save files.", ShadowToast.getTextOfLatestToast())
        assertEquals(emptyList<Uri>(), exported)
    }

    // The file picker takes a moment to cover BlueCard, so the second tap reaches the button.
    @Test
    fun export_doubleTap_opensFilePickerOnce() {
        show()

        exportButton().performTouchInput { doubleClick() }

        assertEquals(1, launchedForResult.size)
    }

    @Test
    fun import_asksForAnyFile_andReadsIt() {
        show()

        importButton().performScrollTo().performClick()

        val picker = launchedForResult.single()
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.action)
        // Only documents it can read, not virtual ones such as a Google Doc.
        assertEquals(setOf(Intent.CATEGORY_OPENABLE), picker.categories)
        assertEquals(listOf("*/*"), picker.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList())
        assertEquals(listOf(file), read)
        assertEquals(0, merges + replaces)
    }

    @Test
    fun import_leavingTheFilePickerWithoutChoosing_readsNothing() {
        pickFile = { null }
        show()

        importButton().performScrollTo().performClick()

        assertEquals(1, launchedForResult.size)
        assertEquals(emptyList<Uri>(), read)
    }

    @Test
    fun import_withNoFilePicker_showsMessage() {
        pickFile = { throw ActivityNotFoundException() }
        show()

        importButton().performScrollTo().performClick()

        assertEquals("No app on this phone can open files.", ShadowToast.getTextOfLatestToast())
        assertEquals(emptyList<Uri>(), read)
    }

    @Test
    fun whileWorking_theButtonsWait() {
        show(started.copy(working = true))

        editProfileButton().assertIsNotEnabled()
        exportButton().assertIsNotEnabled()
        importButton().assertIsNotEnabled()
        clearButton().assertIsNotEnabled()
    }

    @Test
    fun clearAll_withNoBadgeStarted_isDisabled() {
        show()

        clearButton().assertIsNotEnabled()
        exportButton().assertIsEnabled()
    }

    @Test
    fun fileToImport_asksWhetherToMergeOrReplaceEverything() {
        show(ready.copy(backupToImport = backup))

        composeTestRule.onNodeWithText("Import this file?").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "Merge adds the file's badges and ranks to yours, and asks which to keep where " +
                    "the two differ. Replace all replaces your name, unit number and all your " +
                    "progress with the file's."
            )
            .assertIsDisplayed()
        assertEquals(0, merges + replaces)
    }

    @Test
    fun fileToImport_merge_mergesIt() {
        show(ready.copy(backupToImport = backup))

        composeTestRule.onNodeWithText("Merge").performClick()

        assertEquals(1, merges)
        assertEquals(0, replaces + importsCancelled)
    }

    @Test
    fun fileToImport_replaceAll_replacesEverything() {
        show(ready.copy(backupToImport = backup))

        composeTestRule.onNodeWithText("Replace all").performClick()

        assertEquals(1, replaces)
        assertEquals(0, merges + importsCancelled)
    }

    @Test
    fun fileToImport_cancel_cancelsTheImport() {
        show(ready.copy(backupToImport = backup))

        composeTestRule.onNodeWithText("Cancel").performClick()

        assertEquals(1, importsCancelled)
        assertEquals(0, merges + replaces)
    }

    @Test
    fun fileToImport_back_cancelsTheImport() {
        show(ready.copy(backupToImport = backup))

        // Espresso's pressBack doesn't reach the dialog's window under Robolectric, so Back is
        // sent to the dialog itself.
        composeTestRule.runOnIdle {
            (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher
                .onBackPressed()
        }

        assertEquals(1, importsCancelled)
        assertEquals(0, merges + replaces)
    }

    private val choices = MergeChoices(
        MergeSources(emptyList(), emptyList(), backup, backup),
        ProfileChoice(Profile("Sam Scout", "Crew 7"), Profile("Sam Lee", "Troop 12")),
        listOf(
            AdvancementChoice(
                "camping",
                "Camping",
                isRank = false,
                phone = ProgressSummary(done = false, fractionDone = 0.4f),
                file = ProgressSummary(done = true, doneOn = LocalDate.of(2026, 4, 15))
            ),
            AdvancementChoice(
                "cooking",
                "Cooking",
                isRank = false,
                // One that can't be measured, on requirements this version doesn't have.
                phone = ProgressSummary(
                    done = false,
                    requirementsVersion = LocalDate.of(2025, 1, 1)
                ),
                file = ProgressSummary(
                    done = false,
                    fractionDone = 0.25f,
                    requirementsVersion = LocalDate.of(2026, 1, 1)
                )
            ),
            AdvancementChoice(
                "scout",
                "Scout",
                isRank = true,
                phone = ProgressSummary(done = false, fractionDone = 0.5f),
                file = ProgressSummary(done = true),
                fromFile = true
            )
        )
    )

    /**
     * Shows the merge's [choices], once they take taps
     * (mergeChoices_ignoreTapsForTheDoubleTapTimeout_asTheyOpen).
     */
    private fun showMergeChoices(choices: MergeChoices) {
        show(ready.copy(mergeChoices = choices))
        composeTestRule.mainClock.advanceTimeBy(ViewConfiguration.getDoubleTapTimeout().toLong())
    }

    /** In the merge's dialog, not the page under it. */
    private fun inDialog(matcher: SemanticsMatcher) = matcher and hasAnyAncestor(isDialog())

    /** The option for the phone's or the file's ([label]) that holds [details]. */
    private fun option(label: String, vararg details: String) = composeTestRule.onNode(
        details.fold(inDialog(hasText(label))) { matcher, detail -> matcher and hasText(detail) }
    ).performScrollTo()

    @Test
    fun mergeChoices_showEachChoice_underItsHeading_withWhatEachSideHolds() {
        showMergeChoices(choices)

        // The title stays at the top as the choices scroll.
        composeTestRule.onNode(inDialog(hasTextAndHeading("Merge"))).assertIsDisplayed()
        composeTestRule
            .onNodeWithText("This phone and the file are different here. Choose which to keep.")
            .assertIsDisplayed()
        for (heading in listOf("Name and unit", "Camping", "Cooking", "Scout")) {
            composeTestRule.onNode(inDialog(hasTextAndHeading(heading)))
                .performScrollTo()
                .assertIsDisplayed()
        }
        option("This phone", "Sam Scout", "Unit: Crew 7").assertIsSelected()
        option("The file", "Sam Lee", "Unit: Troop 12").assertIsNotSelected()
        option("This phone", "40% done").assertIsSelected()
        option("The file", "Completed on Apr 15, 2026").assertIsNotSelected()
        option("This phone", "In progress", "Requirements effective Jan 1, 2025")
            .assertIsSelected()
        option("The file", "25% done", "Requirements effective Jan 1, 2026").assertIsNotSelected()
        option("This phone", "50% done").assertIsNotSelected()
        option("The file", "Earned").assertIsSelected()
    }

    // Screen readers hear each option as a radio button, in a group of two.
    @Test
    fun mergeChoices_areRadioButtons_inGroups() {
        showMergeChoices(choices)

        val options = composeTestRule.onAllNodes(inDialog(isSelectable()))
        options.assertCountEquals(8)
        options.assertAll(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton) and
                hasParent(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        )
    }

    @Test
    fun mergeChoices_withTheSameProfile_dontAskAboutIt() {
        showMergeChoices(choices.copy(profile = null))

        composeTestRule.onNode(inDialog(hasText("Name and unit"))).assertDoesNotExist()
        composeTestRule.onNode(inDialog(hasText("Sam Scout"))).assertDoesNotExist()
    }

    @Test
    fun mergeChoices_tappingAnOption_choosesIt() {
        showMergeChoices(choices)

        option("The file", "Sam Lee").performClick()
        option("The file", "Completed on Apr 15, 2026").performClick()
        option("This phone", "50% done").performClick()

        assertEquals(listOf(true), profileChoices)
        assertEquals(listOf("camping" to true, "scout" to false), progressChoices)
    }

    @Test
    fun mergeChoices_merge_confirmsTheMerge() {
        showMergeChoices(choices)

        composeTestRule.onNode(inDialog(hasText("Merge") and hasClickAction())).performClick()

        assertEquals(1, mergesConfirmed)
        assertEquals(0, importsCancelled)
    }

    /** [choices] with the phone's chosen for everything, as they start. */
    private val unchanged = choices.copy(
        advancements = choices.advancements.map { it.copy(fromFile = false) }
    )

    private fun closeButton() = composeTestRule.onNodeWithContentDescription("Close")

    /** Back, sent to the topmost dialog, as in fileToImport_back_cancelsTheImport. */
    private fun pressBackOnTopDialog() = composeTestRule.runOnIdle {
        (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
    }

    @Test
    fun mergeChoices_unchanged_close_cancelsTheImport() {
        showMergeChoices(unchanged)

        closeButton().performClick()

        assertEquals(1, importsCancelled)
        assertEquals(0, mergesConfirmed)
    }

    @Test
    fun mergeChoices_unchanged_back_cancelsTheImport() {
        showMergeChoices(unchanged)

        pressBackOnTopDialog()

        assertEquals(1, importsCancelled)
        assertEquals(0, mergesConfirmed)
    }

    @Test
    fun mergeChoices_withTheFilesChosen_close_asksBeforeDiscarding_thenCancels() {
        showMergeChoices(choices)

        closeButton().performClick()

        composeTestRule.onNodeWithText("Discard changes?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Your changes haven't been saved.").assertIsDisplayed()
        assertEquals(0, importsCancelled)

        composeTestRule.onNodeWithText("Discard").performClick()

        assertEquals(1, importsCancelled)
        assertEquals(0, mergesConfirmed)
    }

    @Test
    fun mergeChoices_withTheFilesChosen_back_asks_andCancelKeepsTheChoices() {
        showMergeChoices(choices)

        pressBackOnTopDialog()
        composeTestRule.onNodeWithText("Discard changes?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()

        composeTestRule.onNodeWithText("Discard changes?").assertDoesNotExist()
        option("The file", "Earned").assertIsSelected()
        assertEquals(0, importsCancelled)
    }

    @Test
    fun mergeChoices_namesTheRanksTheMergeWouldUnearn() {
        showMergeChoices(choices.copy(unearnedRanks = listOf("Scout", "Tenderfoot")))

        // A live region, so screen readers announce it as the scout chooses.
        composeTestRule
            .onNodeWithText("Scout and Tenderfoot will no longer count as earned.")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, Polite))
    }

    // A screen reader user moving from control to control doesn't hear the heading, so each
    // option's label names it. The label is on the text, as for ButtonText.
    @Test
    fun mergeChoices_optionsNameWhatTheyreFor_toScreenReaders() {
        showMergeChoices(choices)

        for (description in listOf(
            "Name and unit, this phone",
            "Name and unit, the file",
            "Camping, this phone",
            "Scout, the file"
        )) {
            composeTestRule
                .onNode(hasContentDescription(description), useUnmergedTree = true)
                .assert(hasText(description.substringAfter(", "), ignoreCase = true))
        }
    }

    // The second tap of a double tap on the import dialog's Merge would land on the merge's
    // dialog as it opens.
    @Test
    fun mergeChoices_ignoreTapsForTheDoubleTapTimeout_asTheyOpen() {
        composeTestRule.mainClock.autoAdvance = false
        show(ready.copy(mergeChoices = unchanged))
        composeTestRule.mainClock.advanceTimeByFrame()
        val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()

        // Near the top, so no scrolling, which would wait on the clock this test holds.
        val filesProfile = composeTestRule.onNode(
            inDialog(hasText("The file") and hasText("Sam Lee"))
        )

        filesProfile.performClick()
        composeTestRule.mainClock.advanceTimeBy(doubleTapTimeout / 2)
        assertEquals(emptyList<Boolean>(), profileChoices)

        composeTestRule.mainClock.advanceTimeBy(doubleTapTimeout)
        filesProfile.performClick()
        assertEquals(listOf(true), profileChoices)
    }

    @Test
    fun clearAll_asksFirst_thenClears() {
        show(started)

        clearButton().performClick()
        composeTestRule.onNodeWithText("Clear all progress?").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "The progress on every badge and rank will be removed, including " +
                    "counselors. Your name and unit number stay."
            )
            .assertIsDisplayed()
        assertEquals(0, clears)
        composeTestRule.onNodeWithText("Clear").performClick()

        assertEquals(1, clears)
        composeTestRule.onNodeWithText("Clear all progress?").assertDoesNotExist()
    }

    @Test
    fun clearAll_cancel_clearsNothing() {
        show(started)

        clearButton().performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()

        assertEquals(0, clears)
        composeTestRule.onNodeWithText("Clear all progress?").assertDoesNotExist()
    }

    @Test
    fun clearAll_back_closesTheDialog_andClearsNothing() {
        show(started)

        clearButton().performClick()
        // As for the import dialog, Back is sent to the dialog itself.
        composeTestRule.runOnIdle {
            (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher
                .onBackPressed()
        }

        assertEquals(0, clears)
        composeTestRule.onNodeWithText("Clear all progress?").assertDoesNotExist()
    }

    // The file picker takes a moment to cover BlueCard. A tap on Clear all that reached it then
    // would open the dialog under the picker, to be confirmed after the import. The screen is
    // tall enough to show every button, since scrolling waits on the clock this test holds.
    @Test
    @Config(qualifiers = "h800dp")
    fun clearAll_rightAfterImport_isIgnored() {
        show(started)
        composeTestRule.mainClock.autoAdvance = false
        val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()
        val clearButton = composeTestRule.onNodeWithText("Clear all")

        importButton().performClick()
        clearButton.performClick()
        composeTestRule.mainClock.advanceTimeBy(doubleTapTimeout / 2)
        composeTestRule.onNodeWithText("Clear all progress?").assertDoesNotExist()

        composeTestRule.mainClock.advanceTimeBy(doubleTapTimeout)
        clearButton.performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText("Clear all progress?").assertIsDisplayed()
        assertEquals(listOf(file), read)
    }

    @Test
    fun eachMessage_isShown_thenReportedShown() {
        val texts = mapOf(
            Kind.ExportFailed to "Couldn't export. Try again.",
            Kind.ReadFailed to "Couldn't open the file. Try again.",
            Kind.Invalid to "This file isn't a BlueCard export.",
            Kind.NewerFormat to
                "This file is from a newer version of BlueCard. Update the app to import it.",
            Kind.ImportFailed to "Couldn't import. Try again.",
            Kind.Imported to "Data imported.",
            Kind.Merged to "Data merged.",
            Kind.ClearFailed to "Couldn't save. Try again.",
            Kind.Cleared to "Progress cleared."
        )
        assertEquals(Kind.entries.toSet(), texts.keys)
        show()

        for ((kind, text) in texts) {
            val message = DataManagementMessage(kind)
            uiState = ready.copy(message = message)

            composeTestRule.onNodeWithText(text).assertIsDisplayed()
            // A short snackbar shows for 4 seconds.
            composeTestRule.mainClock.advanceTimeBy(5_000)
            composeTestRule.onNodeWithText(text).assertDoesNotExist()
            assertEquals(message, messagesShown.last())
        }
    }
}
