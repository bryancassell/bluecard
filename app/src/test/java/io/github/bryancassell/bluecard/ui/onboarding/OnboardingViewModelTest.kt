package io.github.bryancassell.bluecard.ui.onboarding

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.profile.FakeProfileRepository
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.testing.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

// Robolectric, because a failed save reaches android.util.Log, which throws in plain local
// tests (see ARCHITECTURE.md, Testing approach).
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class OnboardingViewModelTest {
    // A standard dispatcher queues the save, so tests can see the saving state.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val repository = FakeProfileRepository()
    private val savedStateHandle = SavedStateHandle()
    private val viewModel = OnboardingViewModel(repository, savedStateHandle)

    private val state get() = viewModel.uiState.value

    private fun fillIn(name: String = "Alex Scout", unitNumber: String = "123") {
        viewModel.onNameChange(name)
        viewModel.onUnitNumberChange(unitNumber)
    }

    @Test
    fun uiState_startsEmptyAndCannotSave() {
        assertEquals(OnboardingUiState(), state)
        assertTrue(state.canEdit)
        assertFalse(state.canSave)
    }

    @Test
    fun typing_updatesFields() {
        fillIn()

        assertEquals("Alex Scout", state.name)
        assertEquals("123", state.unitNumber)
        assertTrue(state.canSave)
    }

    @Test
    fun typing_isRestoredFromSavedState() {
        fillIn()

        // A new ViewModel with the same saved state, as after the system stopped the app.
        val restored = OnboardingViewModel(repository, savedStateHandle).uiState.value

        assertEquals("Alex Scout", restored.name)
        assertEquals("123", restored.unitNumber)
    }

    @Test
    fun canSave_needsBothFields() {
        fillIn(name = "Alex Scout", unitNumber = "")
        assertFalse(state.canSave)

        fillIn(name = "", unitNumber = "123")
        assertFalse(state.canSave)
    }

    @Test
    fun canSave_blankIsEmpty() {
        fillIn(name = "  ", unitNumber = "123")

        assertFalse(state.canSave)
    }

    @Test
    fun unitNumber_acceptsAnyText() = runTest {
        fillIn(unitNumber = "Troop 123B")
        viewModel.save()
        advanceUntilIdle()

        assertEquals("Troop 123B", repository.observeProfile().first()?.unitNumber)
    }

    @Test
    fun save_showsSavingThenSaved_andLocksTheForm() = runTest {
        fillIn()
        viewModel.save()

        assertEquals(SaveStatus.Saving, state.saveStatus)
        assertFalse(state.canEdit)
        assertFalse(state.canSave)

        advanceUntilIdle()

        assertEquals(SaveStatus.Saved, state.saveStatus)
        assertFalse(state.canEdit)
        assertFalse(state.canSave)
    }

    @Test
    fun save_storesTrimmedProfile() = runTest {
        fillIn(name = "  Alex Scout ", unitNumber = " 123 ")
        viewModel.save()
        advanceUntilIdle()

        assertEquals(Profile("Alex Scout", "123"), repository.observeProfile().first())
    }

    @Test
    fun save_withMissingField_doesNothing() = runTest {
        fillIn(unitNumber = "")
        viewModel.save()
        advanceUntilIdle()

        assertNull(repository.observeProfile().first())
        assertEquals(SaveStatus.Editing, state.saveStatus)
    }

    @Test
    fun save_whenItFails_showsFailedAndAllowsRetry() = runTest {
        repository.failSaves = true
        fillIn()
        viewModel.save()
        advanceUntilIdle()

        assertEquals(SaveStatus.Failed, state.saveStatus)
        assertTrue(state.canEdit)
        assertTrue(state.canSave)
        val log = ShadowLog.getLogsForTag("Onboarding").single()
        assertEquals(Log.WARN, log.type)
        assertTrue(log.throwable is IOException)

        repository.failSaves = false
        viewModel.save()
        advanceUntilIdle()

        assertEquals(SaveStatus.Saved, state.saveStatus)
        assertEquals(Profile("Alex Scout", "123"), repository.observeProfile().first())
    }
}
