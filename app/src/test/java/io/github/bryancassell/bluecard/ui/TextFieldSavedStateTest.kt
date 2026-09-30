package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.testing.viewModelScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [textFieldState], [restoredText], [keepText] and [StoredTextFields]. Robolectric, because saved
 * state is a Bundle, and ViewModelScenario passes it through a Parcel as the system does.
 */
@RunWith(AndroidJUnit4::class)
class TextFieldSavedStateTest {
    private class FieldViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
        val field = savedStateHandle.textFieldState("field")
    }

    /** Keeps its field only once [startKeeping] is called. */
    private class KeptLaterViewModel(private val savedStateHandle: SavedStateHandle) :
        ViewModel() {
        val restored = savedStateHandle.restoredText("field")
        val field = TextFieldState(restored.orEmpty())

        fun startKeeping() = savedStateHandle.keepText("field", field)
    }

    private class StoredFieldsViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
        val fields = StoredTextFields(savedStateHandle, "name", "phone")
        val name = fields["name"]
        val phone = fields["phone"]
    }

    private val stored = mapOf("name" to "Pat Lee", "phone" to null)

    /** Keeps only a name field, so a restored StoredFieldsViewModel finds only "name" kept. */
    private class NameKeptAloneViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
        init {
            savedStateHandle.keepText("name", TextFieldState("Kept alone"))
        }
    }

    @Test
    fun textFieldState_withNothingKept_startsEmpty() {
        viewModelScenario { FieldViewModel(createSavedStateHandle()) }.use { scenario ->
            assertEquals("", scenario.viewModel.field.text.toString())
        }
    }

    @Test
    fun textFieldState_keepsTheTextWhenTheSystemStopsTheApp() {
        viewModelScenario { FieldViewModel(createSavedStateHandle()) }.use { scenario ->
            scenario.viewModel.field.setTextAndPlaceCursorAtEnd("Camp")

            // Saves state, then restores it into a new ViewModel.
            scenario.recreate()

            assertEquals("Camp", scenario.viewModel.field.text.toString())
        }
    }

    @Test
    fun textFieldState_keepsTheLatestTextEachTime() {
        viewModelScenario { FieldViewModel(createSavedStateHandle()) }.use { scenario ->
            scenario.viewModel.field.setTextAndPlaceCursorAtEnd("Camp")
            scenario.recreate()

            scenario.viewModel.field.setTextAndPlaceCursorAtEnd("Camping")
            scenario.recreate()

            assertEquals("Camping", scenario.viewModel.field.text.toString())
        }
    }

    @Test
    fun textFieldState_keepsAClearedField() {
        viewModelScenario { FieldViewModel(createSavedStateHandle()) }.use { scenario ->
            scenario.viewModel.field.setTextAndPlaceCursorAtEnd("Camp")
            scenario.recreate()

            scenario.viewModel.field.clearText()
            scenario.recreate()

            assertEquals("", scenario.viewModel.field.text.toString())
        }
    }

    @Test
    fun textFieldState_ignoresAnotherValueUnderItsKey() {
        // A value keepText didn't keep.
        val savedStateHandle = SavedStateHandle(mapOf("field" to "From an intent."))

        assertEquals("", savedStateHandle.textFieldState("field").text.toString())
        assertNull(savedStateHandle.restoredText("field"))
    }

    @Test
    fun restoredText_whenNothingWasKept_isNull() {
        viewModelScenario { KeptLaterViewModel(createSavedStateHandle()) }.use { scenario ->
            scenario.viewModel.field.setTextAndPlaceCursorAtEnd("Not kept.")

            scenario.recreate()

            assertNull(scenario.viewModel.restored)
        }
    }

    @Test
    fun keepText_keepsTheTextFromWhenItsCalled() {
        viewModelScenario { KeptLaterViewModel(createSavedStateHandle()) }.use { scenario ->
            scenario.viewModel.startKeeping()
            scenario.viewModel.field.setTextAndPlaceCursorAtEnd("Kept.")

            scenario.recreate()

            assertEquals("Kept.", scenario.viewModel.restored)
        }
    }

    @Test
    fun storedTextFields_startEmpty_thenLoadTheStoredText() {
        viewModelScenario { StoredFieldsViewModel(createSavedStateHandle()) }.use { scenario ->
            val viewModel = scenario.viewModel
            assertEquals("", viewModel.name.text.toString())

            viewModel.fields.loadOnce { stored }

            assertEquals("Pat Lee", viewModel.name.text.toString())
            assertEquals("", viewModel.phone.text.toString())
        }
    }

    @Test
    fun storedTextFields_loadOnlyOnce() {
        viewModelScenario { StoredFieldsViewModel(createSavedStateHandle()) }.use { scenario ->
            val viewModel = scenario.viewModel
            viewModel.fields.loadOnce { stored }
            viewModel.name.setTextAndPlaceCursorAtEnd("Sam Park")

            var calls = 0
            viewModel.fields.loadOnce {
                calls++
                mapOf("name" to "Someone else")
            }

            assertEquals("Sam Park", viewModel.name.text.toString())
            assertEquals(0, calls)
        }
    }

    @Test
    fun storedTextFields_keepTheirTextOnceLoaded_andDontLoadAgain() {
        viewModelScenario { StoredFieldsViewModel(createSavedStateHandle()) }.use { scenario ->
            scenario.viewModel.fields.loadOnce { stored }
            scenario.viewModel.name.setTextAndPlaceCursorAtEnd("Sam Park")
            scenario.viewModel.phone.setTextAndPlaceCursorAtEnd("555-0100")

            scenario.recreate()
            val restored = scenario.viewModel
            restored.fields.loadOnce { stored }

            assertEquals("Sam Park", restored.name.text.toString())
            assertEquals("555-0100", restored.phone.text.toString())
        }
    }

    @Test
    fun storedTextFields_stoppedBeforeLoading_loadTheStoredTextAgain() {
        viewModelScenario { StoredFieldsViewModel(createSavedStateHandle()) }.use { scenario ->
            scenario.viewModel.name.setTextAndPlaceCursorAtEnd("Not kept.")

            scenario.recreate()
            val restored = scenario.viewModel
            assertEquals("", restored.name.text.toString())
            restored.fields.loadOnce { stored }

            assertEquals("Pat Lee", restored.name.text.toString())
        }
    }

    @Test
    fun storedTextFields_withOnlySomeKept_loadTheStoredTextIntoAll() {
        // They're kept together, so one kept without the others isn't trusted.
        var restoring = false
        viewModelScenario<ViewModel> {
            if (restoring) {
                StoredFieldsViewModel(createSavedStateHandle())
            } else {
                NameKeptAloneViewModel(createSavedStateHandle())
            }
        }.use { scenario ->
            // Created when first read.
            scenario.viewModel
            restoring = true

            scenario.recreate()
            val restored = scenario.viewModel as StoredFieldsViewModel
            restored.fields.loadOnce { stored }

            assertEquals("Pat Lee", restored.name.text.toString())
        }
    }

    @Test
    fun storedTextFields_withAnotherValueUnderAKey_loadTheStoredText() {
        // As when the intent that opened the app has an extra with the same name.
        val fields = StoredTextFields(SavedStateHandle(mapOf("name" to "Intent")), "name", "phone")

        fields.loadOnce { stored }

        assertEquals("Pat Lee", fields["name"].text.toString())
    }
}
