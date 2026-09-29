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
 * [textFieldState], [restoredText] and [keepText]. Robolectric, because saved state is a
 * Bundle, and ViewModelScenario passes it through a Parcel as the system does.
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
}
