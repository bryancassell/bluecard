package io.github.bryancassell.bluecard.ui.onboarding

import android.util.Log
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.testing.viewModelScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.profile.FakeProfileRepository
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.testing.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
@RunWith(AndroidJUnit4::class)
class OnboardingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeProfileRepository()
    private val savedStateHandle = SavedStateHandle()

    // Created in the test, after MainDispatcherRule has replaced the Main dispatcher that
    // viewModelScope uses.
    private val viewModel by lazy { OnboardingViewModel(repository, savedStateHandle) }

    private val state get() = viewModel.uiState.value

    /** Saves that never finish, so the ViewModel stays in the saving state. */
    private class NeverFinishingProfileRepository : ProfileRepository {
        var saves = 0

        override fun observeProfile(): Flow<Profile?> = flowOf(null)

        override suspend fun saveProfile(profile: Profile) {
            saves++
            awaitCancellation()
        }
    }

    /**
     * Collects uiState, as the screen does, so WhileSubscribed starts it. From the
     * coroutines testing guide: https://developer.android.com/kotlin/coroutines/test#statein
     */
    private fun TestScope.startCollecting(
        viewModel: OnboardingViewModel = this@OnboardingViewModelTest.viewModel
    ) {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }
    }

    /** Types into the fields, replacing what's there. */
    private fun fillIn(
        name: String = "Alex Scout",
        unitNumber: String = "123",
        viewModel: OnboardingViewModel = this.viewModel
    ) {
        viewModel.name.setTextAndPlaceCursorAtEnd(name)
        viewModel.unitNumber.setTextAndPlaceCursorAtEnd(unitNumber)
        // Compose announces snapshot changes, which snapshotFlow waits for, once per frame;
        // outside a composition, tests announce them.
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun uiState_startsEmptyAndCannotSave() = runTest {
        startCollecting()

        assertEquals("", viewModel.name.text.toString())
        assertEquals("", viewModel.unitNumber.text.toString())
        assertEquals(OnboardingUiState(), state)
        assertTrue(state.canEdit)
        assertFalse(state.canSave)
    }

    @Test
    fun typing_inBothFields_allowsSave() = runTest {
        startCollecting()
        fillIn()

        assertTrue(state.isComplete)
        assertTrue(state.canSave)
    }

    @Test
    fun typing_isRestoredFromSavedState() = runTest {
        viewModelScenario { OnboardingViewModel(repository, createSavedStateHandle()) }
            .use { scenario ->
                // While nothing collects uiState.
                fillIn(viewModel = scenario.viewModel)

                // Saves state and restores it into a new ViewModel, as when the system stops
                // the app.
                scenario.recreate()
                val restored = scenario.viewModel

                assertEquals("Alex Scout", restored.name.text.toString())
                assertEquals("123", restored.unitNumber.text.toString())
                // Before anything collects it, so Save is enabled from the first frame.
                assertTrue(restored.uiState.value.canSave)
            }
    }

    @Test
    fun canSave_needsBothFields() = runTest {
        startCollecting()
        fillIn()
        assertTrue(state.canSave)

        fillIn(name = "Alex Scout", unitNumber = "")
        assertFalse(state.canSave)

        fillIn(name = "", unitNumber = "123")
        assertFalse(state.canSave)
    }

    @Test
    fun canSave_blankIsEmpty() = runTest {
        startCollecting()
        fillIn(name = "  ", unitNumber = "123")

        assertFalse(state.canSave)
    }

    @Test
    fun unitNumber_acceptsAnyText() = runTest {
        fillIn(unitNumber = "Troop 123B")
        viewModel.save()

        assertEquals("Troop 123B", repository.observeProfile().first()?.unitNumber)
    }

    @Test
    fun save_whileSaving_showsSavingAndLocksTheForm() = runTest {
        val viewModel = OnboardingViewModel(NeverFinishingProfileRepository(), savedStateHandle)
        startCollecting(viewModel)
        fillIn(viewModel = viewModel)

        viewModel.save()

        val state = viewModel.uiState.value
        assertEquals(SaveStatus.Saving, state.saveStatus)
        assertFalse(state.canEdit)
        assertFalse(state.canSave)
    }

    @Test
    fun save_whileSaving_ignoresAnotherSave() = runTest {
        val repository = NeverFinishingProfileRepository()
        val viewModel = OnboardingViewModel(repository, savedStateHandle)
        fillIn(viewModel = viewModel)

        // As when the scout presses Done and then taps Get started.
        viewModel.save()
        viewModel.save()

        assertEquals(1, repository.saves)
    }

    @Test
    fun save_whenDone_showsSavedAndLocksTheForm() = runTest {
        startCollecting()
        fillIn()

        viewModel.save()

        assertEquals(SaveStatus.Saved, state.saveStatus)
        assertFalse(state.canEdit)
        assertFalse(state.canSave)
    }

    @Test
    fun save_storesTrimmedProfile() = runTest {
        fillIn(name = "  Alex Scout ", unitNumber = " 123 ")
        viewModel.save()

        assertEquals(Profile("Alex Scout", "123"), repository.observeProfile().first())
    }

    @Test
    fun save_rightAfterTyping_storesWhatWasTyped() = runTest {
        startCollecting()
        // Typed, but not yet announced, so uiState hasn't caught up.
        viewModel.name.setTextAndPlaceCursorAtEnd("Alex Scout")
        viewModel.unitNumber.setTextAndPlaceCursorAtEnd("123")
        assertFalse(state.canSave)

        viewModel.save()

        assertEquals(Profile("Alex Scout", "123"), repository.observeProfile().first())
    }

    @Test
    fun save_withMissingField_doesNothing() = runTest {
        startCollecting()
        fillIn(unitNumber = "")
        viewModel.save()

        assertNull(repository.observeProfile().first())
        assertEquals(SaveStatus.Editing, state.saveStatus)
    }

    @Test
    fun save_whenItFails_showsFailedAndAllowsRetry() = runTest {
        repository.failSaves = true
        startCollecting()
        fillIn()
        viewModel.save()

        assertEquals(SaveStatus.Failed, state.saveStatus)
        assertTrue(state.canEdit)
        assertTrue(state.canSave)
        val log = ShadowLog.getLogsForTag("Onboarding").single()
        assertEquals(Log.WARN, log.type)
        assertTrue(log.throwable is IOException)

        repository.failSaves = false
        viewModel.save()

        assertEquals(SaveStatus.Saved, state.saveStatus)
        assertEquals(Profile("Alex Scout", "123"), repository.observeProfile().first())
    }
}
