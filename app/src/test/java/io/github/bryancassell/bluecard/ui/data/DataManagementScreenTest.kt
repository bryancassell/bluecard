package io.github.bryancassell.bluecard.ui.data

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentDialog
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.backup.Backup
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.ui.data.DataManagementMessage.Kind
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast

@RunWith(AndroidJUnit4::class)
class DataManagementScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val exported = mutableListOf<Uri>()
    private val read = mutableListOf<Uri>()
    private var importsConfirmed = 0
    private var importsCancelled = 0
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

    private val ready = DataManagementUiState(today = LocalDate.of(2026, 10, 1))
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
                    onExport = { exported += it },
                    onImport = { read += it },
                    onConfirmImport = { importsConfirmed++ },
                    onCancelImport = { importsCancelled++ },
                    onMessageShown = { messagesShown += it }
                )
            }
        }
    }

    private fun exportButton() = composeTestRule.onNodeWithText("Export")

    private fun importButton() = composeTestRule.onNodeWithText("Import")

    @Test
    fun showsWhatExportAndImportDo_underHeadings() {
        show()

        for (heading in listOf("Data management", "Export data", "Import data")) {
            composeTestRule.onNode(hasTextAndHeading(heading)).assertIsDisplayed()
        }
        composeTestRule
            .onNodeWithText("Save your name, unit number and all your progress to a file.")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Replace everything on this phone with a file you exported.")
            .assertIsDisplayed()
        exportButton().assertIsEnabled()
        importButton().assertIsEnabled()
    }

    private fun hasTextAndHeading(text: String) = hasText(text) and isHeading()

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

        importButton().performClick()

        val picker = launchedForResult.single()
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.action)
        // Only documents it can read, not virtual ones such as a Google Doc.
        assertEquals(setOf(Intent.CATEGORY_OPENABLE), picker.categories)
        assertEquals(listOf("*/*"), picker.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList())
        assertEquals(listOf(file), read)
        assertEquals(0, importsConfirmed)
    }

    @Test
    fun import_leavingTheFilePickerWithoutChoosing_readsNothing() {
        pickFile = { null }
        show()

        importButton().performClick()

        assertEquals(1, launchedForResult.size)
        assertEquals(emptyList<Uri>(), read)
    }

    @Test
    fun import_withNoFilePicker_showsMessage() {
        pickFile = { throw ActivityNotFoundException() }
        show()

        importButton().performClick()

        assertEquals("No app on this phone can open files.", ShadowToast.getTextOfLatestToast())
        assertEquals(emptyList<Uri>(), read)
    }

    @Test
    fun whileWorking_theButtonsWait() {
        show(ready.copy(working = true))

        exportButton().assertIsNotEnabled()
        importButton().assertIsNotEnabled()
    }

    @Test
    fun fileToImport_asksBeforeReplacingEverything() {
        show(ready.copy(backupToImport = backup))

        composeTestRule.onNodeWithText("Replace all your data?").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "Your name, unit number and all your progress will be replaced with the file's."
            )
            .assertIsDisplayed()
        assertEquals(0, importsConfirmed)
    }

    @Test
    fun fileToImport_replace_confirmsTheImport() {
        show(ready.copy(backupToImport = backup))

        composeTestRule.onNodeWithText("Replace").performClick()

        assertEquals(1, importsConfirmed)
        assertEquals(0, importsCancelled)
    }

    @Test
    fun fileToImport_cancel_cancelsTheImport() {
        show(ready.copy(backupToImport = backup))

        composeTestRule.onNodeWithText("Cancel").performClick()

        assertEquals(1, importsCancelled)
        assertEquals(0, importsConfirmed)
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
        assertEquals(0, importsConfirmed)
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
            Kind.Imported to "Data imported."
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
