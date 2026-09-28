package io.github.bryancassell.bluecard.ui.onboarding

import io.github.bryancassell.bluecard.data.profile.FakeProfileRepository
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.testing.MainDispatcherRule
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

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    // A standard dispatcher queues the save, so tests can see the saving state.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val repository = FakeProfileRepository()
    private val viewModel = OnboardingViewModel(repository)

    private fun fillIn(name: String = "Alex Scout", unitNumber: String = "123") {
        viewModel.onNameChange(name)
        viewModel.onUnitNumberChange(unitNumber)
    }

    @Test
    fun uiState_startsEmptyAndCannotSave() {
        assertEquals(OnboardingUiState(), viewModel.uiState.value)
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun typing_updatesFields() {
        fillIn()

        assertEquals("Alex Scout", viewModel.uiState.value.name)
        assertEquals("123", viewModel.uiState.value.unitNumber)
        assertTrue(viewModel.uiState.value.canSave)
    }

    @Test
    fun canSave_needsBothFields() {
        fillIn(name = "Alex Scout", unitNumber = "")
        assertFalse(viewModel.uiState.value.canSave)

        fillIn(name = "", unitNumber = "123")
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun canSave_blankIsEmpty() {
        fillIn(name = "  ", unitNumber = "123")

        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun unitNumber_acceptsAnyText() = runTest {
        fillIn(unitNumber = "Troop 123B")
        viewModel.save()
        advanceUntilIdle()

        assertEquals("Troop 123B", repository.observeProfile().first()?.unitNumber)
    }

    @Test
    fun save_showsSavingThenSaved() = runTest {
        fillIn()
        viewModel.save()

        assertTrue(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.canSave)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.isSaved)
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
        assertFalse(viewModel.uiState.value.isSaved)
    }
}
