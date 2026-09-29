package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.testing.viewModelScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.catalog.FakeCatalogRepository
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.progress.BadgeProgress
import io.github.bryancassell.bluecard.data.progress.BadgeStart
import io.github.bryancassell.bluecard.data.progress.Counselor
import io.github.bryancassell.bluecard.data.progress.FakeProgressRepository
import io.github.bryancassell.bluecard.testing.MainDispatcherRule
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
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
// plain local tests (see ARCHITECTURE.md, Testing approach).
@RunWith(AndroidJUnit4::class)
class EditCounselorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val older = LocalDate.of(2025, 1, 1)
    private val newest = LocalDate.of(2026, 1, 1)
    private val started = LocalDate.of(2026, 3, 1)
    private val badgeStart = BadgeStart(newest, started)
    private val today = LocalDate.of(2026, 5, 20)
    private val clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    private val camping = MeritBadge(
        id = "camping",
        name = "Camping",
        summary = "Our summary of Camping.",
        officialUrl = "https://www.scouting.org/merit-badges/camping/",
        requirementVersions = listOf(
            RequirementsVersion(older, listOf(Requirement("1", "An older requirement."))),
            RequirementsVersion(newest, listOf(Requirement("1", "Plan a campout.")))
        )
    )

    private val catalogRepository = FakeCatalogRepository(listOf(camping))
    private val progressRepository = FakeProgressRepository()

    private val patLee = Counselor("Pat Lee", "555-0100", "pat@example.com")

    // Created in the test, after MainDispatcherRule has replaced the Main dispatcher that
    // viewModelScope uses.
    private fun viewModel(
        badgeId: String = "camping",
        savedStateHandle: SavedStateHandle = SavedStateHandle()
    ) = EditCounselorViewModel(
        badgeId,
        catalogRepository,
        progressRepository,
        clock,
        savedStateHandle
    )

    /**
     * Can save the ViewModel's state and restore it into a new one, as when the system stops
     * the app.
     */
    private fun scenario() = viewModelScenario {
        EditCounselorViewModel(
            "camping",
            catalogRepository,
            progressRepository,
            clock,
            createSavedStateHandle()
        )
    }

    /**
     * Collects uiState, as the screen does, so WhileSubscribed starts it. From the
     * coroutines testing guide: https://developer.android.com/kotlin/coroutines/test#statein
     */
    private fun TestScope.startCollecting(viewModel: EditCounselorViewModel) {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }
    }

    private fun EditCounselorViewModel.ready() = uiState.value as EditCounselorUiState.Ready

    /** What the fields hold. */
    private fun EditCounselorViewModel.fields() =
        Counselor(name.text.toString(), phone.text.toString(), email.text.toString())

    /** Types into a field, as the scout does. */
    private fun TextFieldState.type(text: String) {
        setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }

    private fun EditCounselorViewModel.typeAll(counselor: Counselor) {
        name.type(counselor.name.orEmpty())
        phone.type(counselor.phone.orEmpty())
        email.type(counselor.email.orEmpty())
    }

    private suspend fun saved() = progressRepository.observeProgress("camping").first()

    private suspend fun saveCounselor(counselor: Counselor) {
        progressRepository.setCounselor("camping", counselor, badgeStart)
    }

    @Test
    fun uiState_whileCatalogLoads_isLoading() = runTest {
        val loading = object : CatalogRepository {
            override suspend fun getBadges(): List<MeritBadge> = awaitCancellation()
        }
        val viewModel = EditCounselorViewModel(
            "camping",
            loading,
            progressRepository,
            clock,
            SavedStateHandle()
        )
        startCollecting(viewModel)

        assertEquals(EditCounselorUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun uiState_whenCatalogCantBeRead_isLoadFailed() = runTest {
        catalogRepository.failLoads = true
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals(EditCounselorUiState.LoadFailed, viewModel.uiState.value)
    }

    @Test
    fun uiState_whenProgressCantBeRead_isLoadFailed() = runTest {
        progressRepository.failLoads = true
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals(EditCounselorUiState.LoadFailed, viewModel.uiState.value)
    }

    @Test
    fun badgeMissingFromCatalog_isUnavailable() = runTest {
        val viewModel = viewModel("retired-badge")
        startCollecting(viewModel)

        assertEquals(EditCounselorUiState.Unavailable, viewModel.uiState.value)
    }

    @Test
    fun startedOnVersionMissingFromCatalog_isUnavailable() = runTest {
        progressRepository.startBadge("camping", LocalDate.of(2023, 1, 1), started)
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals(EditCounselorUiState.Unavailable, viewModel.uiState.value)
    }

    @Test
    fun noCounselor_fieldsStartEmpty_withNothingToSave() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals(
            EditCounselorUiState.Ready(badgeName = "Camping", changed = false),
            viewModel.uiState.value
        )
        assertEquals(Counselor("", "", ""), viewModel.fields())
    }

    @Test
    fun fields_startAsSavedCounselor() = runTest {
        progressRepository.startBadge("camping", newest, started)
        saveCounselor(Counselor(name = "Pat Lee", email = "pat@example.com"))
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals(Counselor("Pat Lee", "", "pat@example.com"), viewModel.fields())
        assertFalse(viewModel.ready().changed)
    }

    @Test
    fun editedFields_areChanged_untilSaved_thenThePageCloses() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)

        viewModel.typeAll(patLee)
        assertTrue(viewModel.ready().changed)
        assertFalse(viewModel.ready().saved)

        viewModel.save()

        assertEquals(patLee, saved()?.badge?.counselor)
        assertFalse(viewModel.ready().changed)
        assertTrue(viewModel.ready().saved)
    }

    @Test
    fun save_onUnstartedBadge_startsItOnNewestVersionToday() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)
        viewModel.name.type("Pat Lee")

        viewModel.save()

        assertEquals(
            BadgeProgress("camping", newest, today, counselor = Counselor(name = "Pat Lee")),
            saved()?.badge
        )
    }

    @Test
    fun save_onBadgeStartedOnOlderVersion_keepsThatVersion() = runTest {
        progressRepository.startBadge("camping", older, started)
        val viewModel = viewModel()
        startCollecting(viewModel)
        viewModel.name.type("Pat Lee")

        viewModel.save()

        assertEquals(
            BadgeProgress("camping", older, started, counselor = Counselor(name = "Pat Lee")),
            saved()?.badge
        )
    }

    @Test
    fun save_trimsSpaces_andOnlySpacesAreNoChange() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)

        viewModel.typeAll(Counselor("  ", " ", ""))
        assertFalse(viewModel.ready().changed)

        viewModel.typeAll(Counselor(" Pat Lee ", "555-0100 ", " pat@example.com"))
        viewModel.save()

        assertEquals(patLee, saved()?.badge?.counselor)
        assertFalse(viewModel.ready().changed)
    }

    @Test
    fun save_someFieldsEmpty_savesTheRest() = runTest {
        progressRepository.startBadge("camping", newest, started)
        saveCounselor(patLee)
        val viewModel = viewModel()
        startCollecting(viewModel)

        viewModel.phone.type("")
        viewModel.save()

        assertEquals(
            Counselor(name = "Pat Lee", email = "pat@example.com"),
            saved()?.badge?.counselor
        )
    }

    @Test
    fun save_everyFieldEmpty_removesTheCounselor() = runTest {
        progressRepository.startBadge("camping", newest, started)
        saveCounselor(patLee)
        val viewModel = viewModel()
        startCollecting(viewModel)

        viewModel.typeAll(Counselor())
        assertTrue(viewModel.ready().changed)
        viewModel.save()

        assertNull(saved()?.badge?.counselor)
        assertTrue(viewModel.ready().saved)
    }

    @Test
    fun unsavedFields_areKeptWhenProgressChanges() = runTest {
        progressRepository.startBadge("camping", newest, started)
        val viewModel = viewModel()
        startCollecting(viewModel)
        viewModel.name.type("Not saved yet")

        // As when a save from another page lands.
        saveCounselor(Counselor(name = "Pat Lee"))

        assertEquals("Not saved yet", viewModel.name.text.toString())
        assertTrue(viewModel.ready().changed)
    }

    @Test
    fun save_whenSaveFails_reportsIt_keepsTheEdits_andStaysOpen() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)
        viewModel.typeAll(patLee)
        progressRepository.failSaves = true

        viewModel.save()

        val failure = viewModel.ready().saveFailure
        assertNotNull(failure)
        assertFalse(viewModel.ready().saved)
        assertEquals(patLee, viewModel.fields())
        assertTrue(viewModel.ready().changed)

        viewModel.onSaveFailureShown(failure!!)

        assertNull(viewModel.ready().saveFailure)
    }

    @Test
    fun unsavedFields_areRestoredFromSavedState() = runTest {
        progressRepository.startBadge("camping", newest, started)
        saveCounselor(patLee)
        scenario().use { scenario ->
            startCollecting(scenario.viewModel)
            scenario.viewModel.name.type("Sam Park")
            scenario.viewModel.phone.type("")

            scenario.recreate()
            val restored = scenario.viewModel
            startCollecting(restored)

            assertEquals(Counselor("Sam Park", "", "pat@example.com"), restored.fields())
            assertTrue(restored.ready().changed)
        }
    }

    @Test
    fun restoredFields_areKeptAgain_evenWhileNothingCollects() = runTest {
        scenario().use { scenario ->
            startCollecting(scenario.viewModel)
            scenario.viewModel.name.type("First edit")
            scenario.recreate()

            // The restored ViewModel, before anything collects its uiState.
            scenario.viewModel.name.setTextAndPlaceCursorAtEnd("Second edit")
            scenario.viewModel.email.setTextAndPlaceCursorAtEnd("pat@example.com")
            scenario.recreate()
            val restored = scenario.viewModel
            startCollecting(restored)

            assertEquals(Counselor("Second edit", "", "pat@example.com"), restored.fields())
        }
    }

    @Test
    fun appStoppedBeforeTheCounselorLoads_loadsTheSavedCounselorAgain() = runTest {
        progressRepository.startBadge("camping", newest, started)
        saveCounselor(patLee)
        scenario().use { scenario ->
            // Nothing has collected uiState, so the saved counselor hasn't loaded.
            assertEquals(Counselor("", "", ""), scenario.viewModel.fields())

            scenario.recreate()
            val restored = scenario.viewModel
            startCollecting(restored)

            assertEquals(patLee, restored.fields())
            assertFalse(restored.ready().changed)
        }
    }

    @Test
    fun anotherValueUnderAFieldsKey_loadsTheSavedCounselor() = runTest {
        progressRepository.startBadge("camping", newest, started)
        saveCounselor(patLee)
        // As when the intent that opened the app has an extra named "name".
        val viewModel = viewModel(savedStateHandle = SavedStateHandle(mapOf("name" to "Intent")))
        startCollecting(viewModel)

        assertEquals(patLee, viewModel.fields())
        assertFalse(viewModel.ready().changed)
    }
}
