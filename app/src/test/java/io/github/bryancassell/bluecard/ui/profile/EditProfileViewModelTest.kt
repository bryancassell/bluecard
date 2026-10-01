package io.github.bryancassell.bluecard.ui.profile

import androidx.compose.foundation.text.input.TextFieldState
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Robolectric, because the load- and save-failure tests reach android.util.Log, which throws in
// plain local tests (see ARCHITECTURE.md, ViewModel tests).
@RunWith(AndroidJUnit4::class)
class EditProfileViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val alex = Profile("Alex Scout", "123")
    private val profileRepository = FakeProfileRepository(alex)

    // Created in the test, after MainDispatcherRule has replaced the Main dispatcher that
    // viewModelScope uses.
    private fun viewModel(
        profileRepository: ProfileRepository = this.profileRepository,
        savedStateHandle: SavedStateHandle = SavedStateHandle()
    ) = EditProfileViewModel(profileRepository, savedStateHandle)

    /**
     * Can save the ViewModel's state and restore it into a new one, as when the system stops
     * the app.
     */
    private fun scenario() = viewModelScenario {
        EditProfileViewModel(profileRepository, createSavedStateHandle())
    }

    /**
     * Collects uiState, as the screen does, so WhileSubscribed starts it. From the
     * coroutines testing guide: https://developer.android.com/kotlin/coroutines/test#statein
     */
    private fun TestScope.startCollecting(viewModel: EditProfileViewModel) {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }
    }

    private fun EditProfileViewModel.ready() = uiState.value as EditProfileUiState.Ready

    /** What the fields hold. */
    private fun EditProfileViewModel.fields() =
        Profile(name.text.toString(), unitNumber.text.toString())

    /** Types into a field, as the scout does. */
    private fun TextFieldState.type(text: String) {
        setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }

    private fun EditProfileViewModel.typeAll(profile: Profile) {
        name.type(profile.name)
        unitNumber.type(profile.unitNumber)
    }

    private suspend fun saved() = profileRepository.observeProfile().first()

    @Test
    fun uiState_whileProfileLoads_isLoading() = runTest {
        val loading = object : ProfileRepository by profileRepository {
            override fun observeProfile(): Flow<Profile?> = flow { awaitCancellation() }
        }
        val viewModel = viewModel(loading)
        startCollecting(viewModel)

        assertEquals(EditProfileUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun uiState_whenProfileCantBeRead_isLoadFailed() = runTest {
        profileRepository.failLoads = true
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals(EditProfileUiState.LoadFailed, viewModel.uiState.value)
    }

    @Test
    fun fields_startAsSavedProfile_withNothingToSave() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals(alex, viewModel.fields())
        assertEquals(EditProfileUiState.Ready(canSave = false), viewModel.uiState.value)
    }

    @Test
    fun editedFields_canBeSaved_untilSaved_thenThePageCloses() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)

        viewModel.typeAll(Profile("Sam Scout", "Crew 7"))
        assertTrue(viewModel.ready().canSave)
        assertFalse(viewModel.ready().saved)

        viewModel.save()

        assertEquals(Profile("Sam Scout", "Crew 7"), saved())
        assertFalse(viewModel.ready().canSave)
        assertTrue(viewModel.ready().saved)
    }

    @Test
    fun oneFieldEdited_savesItWithTheOther() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)

        viewModel.unitNumber.type("Troop 456")
        viewModel.save()

        assertEquals(Profile("Alex Scout", "Troop 456"), saved())
    }

    @Test
    fun blankField_cantBeSaved() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)

        for (blank in listOf(Profile(" ", "456"), Profile("Sam Scout", ""))) {
            viewModel.typeAll(blank)
            assertFalse(viewModel.ready().canSave)

            viewModel.save()

            assertEquals(alex, saved())
            assertFalse(viewModel.ready().saved)
        }
    }

    @Test
    fun save_trimsSpaces_andOnlySpacesAreNoChange() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)

        viewModel.typeAll(Profile(" Alex Scout ", "123 "))
        assertFalse(viewModel.ready().canSave)

        viewModel.typeAll(Profile(" Sam Scout ", " Crew 7"))
        viewModel.save()

        assertEquals(Profile("Sam Scout", "Crew 7"), saved())
    }

    @Test
    fun unsavedFields_areKeptWhenProfileChanges() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)
        viewModel.name.type("Not saved yet")

        // As when an import lands.
        profileRepository.saveProfile(Profile("Sam Scout", "Crew 7"))

        assertEquals(Profile("Not saved yet", "123"), viewModel.fields())
        assertTrue(viewModel.ready().canSave)
    }

    /**
     * A ViewModel whose saves of the profile wait until [allowSave] completes, as when a write
     * waits for another to finish.
     */
    private fun viewModelSavingOnlyAfter(allowSave: CompletableDeferred<Unit>) = viewModel(
        object : ProfileRepository by profileRepository {
            override suspend fun saveProfile(profile: Profile) {
                allowSave.await()
                profileRepository.saveProfile(profile)
            }
        }
    )

    @Test
    fun fieldChangedWhileSaving_staysOpen_toBeSavedToo() = runTest {
        val allowSave = CompletableDeferred<Unit>()
        val viewModel = viewModelSavingOnlyAfter(allowSave)
        startCollecting(viewModel)
        viewModel.name.type("Sam Scout")

        viewModel.save()
        viewModel.name.type("Sam Park")
        allowSave.complete(Unit)

        assertEquals(Profile("Sam Scout", "123"), saved())
        assertFalse(viewModel.ready().saved)
        assertTrue(viewModel.ready().canSave)
        assertEquals("Sam Park", viewModel.name.text.toString())
    }

    @Test
    fun spacesAddedWhileSaving_stillClosesThePage() = runTest {
        val allowSave = CompletableDeferred<Unit>()
        val viewModel = viewModelSavingOnlyAfter(allowSave)
        startCollecting(viewModel)
        viewModel.name.type("Sam Scout")

        viewModel.save()
        // Saved as the same name, so closing loses nothing.
        viewModel.name.type("Sam Scout ")
        allowSave.complete(Unit)

        assertTrue(viewModel.ready().saved)
    }

    @Test
    fun onClosed_letsTheNextSaveCloseThePageAgain() = runTest {
        // As when a screen reader opens the page again before this ViewModel is cleared.
        val viewModel = viewModel()
        startCollecting(viewModel)
        viewModel.name.type("Sam Scout")
        viewModel.save()
        assertTrue(viewModel.ready().saved)

        viewModel.onClosed()

        assertFalse(viewModel.ready().saved)
        viewModel.name.type("Sam Park")
        viewModel.save()
        assertTrue(viewModel.ready().saved)
    }

    @Test
    fun save_whenSaveFails_reportsIt_keepsTheEdits_andStaysOpen() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)
        viewModel.typeAll(Profile("Sam Scout", "Crew 7"))
        profileRepository.failSaves = true

        viewModel.save()

        val failure = viewModel.ready().saveFailure
        assertNotNull(failure)
        assertFalse(viewModel.ready().saved)
        assertEquals(Profile("Sam Scout", "Crew 7"), viewModel.fields())
        assertTrue(viewModel.ready().canSave)

        viewModel.onSaveFailureShown(failure!!)

        assertNull(viewModel.ready().saveFailure)
    }

    @Test
    fun unsavedFields_areRestoredFromSavedState() = runTest {
        scenario().use { scenario ->
            startCollecting(scenario.viewModel)
            scenario.viewModel.name.type("Sam Scout")
            scenario.viewModel.unitNumber.type("")

            scenario.recreate()
            val restored = scenario.viewModel
            startCollecting(restored)

            assertEquals(Profile("Sam Scout", ""), restored.fields())
            assertFalse(restored.ready().canSave)
        }
    }

    @Test
    fun appStoppedBeforeTheProfileLoads_loadsTheSavedProfileAgain() = runTest {
        scenario().use { scenario ->
            // Nothing has collected uiState, so the saved profile hasn't loaded.
            assertEquals(Profile("", ""), scenario.viewModel.fields())

            scenario.recreate()
            val restored = scenario.viewModel
            startCollecting(restored)

            assertEquals(alex, restored.fields())
            assertFalse(restored.ready().canSave)
        }
    }
}
