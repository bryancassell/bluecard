package io.github.bryancassell.bluecard.ui.badge

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
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition
import io.github.bryancassell.bluecard.data.progress.BadgeProgress
import io.github.bryancassell.bluecard.data.progress.BadgeStart
import io.github.bryancassell.bluecard.data.progress.FakeProgressRepository
import io.github.bryancassell.bluecard.data.progress.TrackerEntry
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

// Robolectric, because the load-failure tests reach android.util.Log, which throws in
// plain local tests (see ARCHITECTURE.md, Testing approach).
@RunWith(AndroidJUnit4::class)
class TrackerEntryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val newest = LocalDate.of(2026, 1, 1)
    private val started = LocalDate.of(2026, 3, 1)
    private val badgeStart = BadgeStart(newest, started)
    private val today = LocalDate.of(2026, 5, 20)
    private val clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    // Trackers defined only here, in test catalog data, as a new one would be in the catalog.
    private val sessionColumns = listOf(
        TrackerColumn("date", "Date", TrackerColumnType.DATE),
        TrackerColumn("activity", "Activity", TrackerColumnType.TEXT),
        TrackerColumn("minutes", "Minutes", TrackerColumnType.NUMBER)
    )
    private val weekColumns = listOf(
        TrackerColumn("income", "Income", TrackerColumnType.NUMBER),
        TrackerColumn("notes", "Notes", TrackerColumnType.TEXT)
    )
    private val fitness = MeritBadge(
        id = "personal-fitness",
        name = "Personal Fitness",
        summary = "Our summary of Personal Fitness.",
        officialUrl = "https://www.scouting.org/merit-badges/personal-fitness/",
        requirementVersions = listOf(
            RequirementsVersion(
                newest,
                listOf(
                    Requirement("1", "See your doctor."),
                    Requirement(
                        "2",
                        "Keep a budget for three weeks.",
                        tracker = TrackerDefinition(weekColumns, "week", "weeks", rowCount = 3)
                    ),
                    Requirement(
                        "7a",
                        "Log each session.",
                        tracker = TrackerDefinition(sessionColumns, "session", "sessions")
                    )
                )
            )
        )
    )

    private val catalogRepository = FakeCatalogRepository(listOf(fitness))
    private val progressRepository = FakeProgressRepository()

    // Created in the test, after MainDispatcherRule has replaced the Main dispatcher that
    // viewModelScope uses.
    private fun viewModel(
        number: String = "7a",
        entryId: Long? = null,
        rowNumber: Int? = null,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        catalog: CatalogRepository = catalogRepository
    ) = TrackerEntryViewModel(
        "personal-fitness",
        number,
        entryId,
        rowNumber,
        catalog,
        progressRepository,
        clock,
        savedStateHandle
    )

    /**
     * Can save the ViewModel's state and restore it into a new one, as when the system stops
     * the app.
     */
    private fun scenario(entryId: Long?) = viewModelScenario {
        TrackerEntryViewModel(
            "personal-fitness",
            "7a",
            entryId,
            null,
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
    private fun TestScope.startCollecting(viewModel: TrackerEntryViewModel) {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }
    }

    private fun TrackerEntryViewModel.ready() = uiState.value as TrackerEntryUiState.Ready

    /** Types into a column's field, as the scout does. */
    private fun TrackerEntryViewModel.type(columnId: String, text: String) {
        fields.getValue(columnId).setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }

    private fun TrackerEntryViewModel.text(columnId: String) =
        fields.getValue(columnId).text.toString()

    private suspend fun addSession(values: Map<String, String>) = progressRepository
        .addTrackerEntry("personal-fitness", "7a", null, values, badgeStart)

    private suspend fun entries() =
        progressRepository.observeProgress("personal-fitness").first()?.trackerEntries.orEmpty()

    @Test
    fun uiState_whileCatalogLoads_isLoading() = runTest {
        val loading = object : CatalogRepository {
            override suspend fun getBadges(): List<MeritBadge> = awaitCancellation()
        }
        val viewModel = viewModel(catalog = loading)
        startCollecting(viewModel)

        assertEquals(TrackerEntryUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun uiState_whenCatalogCantBeRead_isLoadFailed() = runTest {
        catalogRepository.failLoads = true
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals(TrackerEntryUiState.LoadFailed, viewModel.uiState.value)
    }

    @Test
    fun uiState_whenProgressCantBeRead_isLoadFailed() = runTest {
        progressRepository.failLoads = true
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals(TrackerEntryUiState.LoadFailed, viewModel.uiState.value)
    }

    @Test
    fun newLogEntry_comesAfterTheOthers_withEmptyFields() = runTest {
        addSession(mapOf("activity" to "Run"))
        addSession(mapOf("activity" to "Swim"))
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals(
            TrackerEntryUiState.Ready(
                badgeName = "Personal Fitness",
                requirementNumber = "7a",
                rowTitle = "Session",
                rowNumber = 3,
                rowLabel = "session",
                columns = sessionColumns,
                dates = emptyMap(),
                canSave = false,
                canDelete = false,
                today = today
            ),
            viewModel.uiState.value
        )
        assertEquals(listOf("", "", ""), sessionColumns.map { viewModel.text(it.id) })
    }

    @Test
    fun savedLogEntry_startsWithItsValues() = runTest {
        addSession(mapOf("activity" to "Run"))
        val id = addSession(mapOf("date" to "2026-04-12", "activity" to "Swim", "minutes" to "45"))
        val viewModel = viewModel(entryId = id)
        startCollecting(viewModel)

        assertEquals(2, viewModel.ready().rowNumber)
        assertEquals(mapOf("date" to LocalDate.of(2026, 4, 12)), viewModel.ready().dates)
        assertEquals(
            listOf("2026-04-12", "Swim", "45"),
            sessionColumns.map {
                viewModel.text(it.id)
            }
        )
        assertFalse(viewModel.ready().canSave)
        assertTrue(viewModel.ready().canDelete)
    }

    @Test
    fun editing_canBeSaved_untilItMatchesWhatsSaved() = runTest {
        val id = addSession(mapOf("activity" to "Run"))
        val viewModel = viewModel(entryId = id)
        startCollecting(viewModel)

        viewModel.type("activity", "Swim")
        assertTrue(viewModel.ready().canSave)

        // Spaces around a value aren't saved, so they're no change.
        viewModel.type("activity", " Run ")
        assertFalse(viewModel.ready().canSave)
    }

    @Test
    fun onlySpaces_cantBeSaved() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)

        viewModel.type("activity", "   ")

        assertFalse(viewModel.ready().canSave)
    }

    @Test
    fun save_newLogEntry_startsTheBadgeAndAddsIt_thenIsDone() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)
        viewModel.type("activity", " Run ")
        viewModel.type("minutes", "30")
        viewModel.setDate("date", LocalDate.of(2026, 5, 1))

        viewModel.save()

        assertEquals(
            BadgeProgress("personal-fitness", newest, today),
            progressRepository.observeProgress("personal-fitness").first()!!.badge
        )
        assertEquals(
            listOf(
                TrackerEntry(
                    1,
                    "personal-fitness",
                    "7a",
                    null,
                    mapOf("date" to "2026-05-01", "activity" to "Run", "minutes" to "30")
                )
            ),
            entries()
        )
        assertTrue(viewModel.ready().done)
        assertFalse(viewModel.ready().canSave)
    }

    @Test
    fun save_savedLogEntry_changesIt() = runTest {
        val id = addSession(mapOf("activity" to "Run", "minutes" to "30"))
        val viewModel = viewModel(entryId = id)
        startCollecting(viewModel)
        viewModel.type("minutes", "")
        viewModel.type("activity", "Swim")

        viewModel.save()

        assertEquals(listOf(mapOf("activity" to "Swim")), entries().map { it.values })
        assertTrue(viewModel.ready().done)
    }

    @Test
    fun save_againOnceSaved_addsNothingMore() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)
        viewModel.type("activity", "Run")

        // As with a double tap on Save.
        viewModel.save()
        viewModel.save()

        assertEquals(1, entries().size)
    }

    @Test
    fun emptyFixedRow_savesIntoThatRow() = runTest {
        val viewModel = viewModel(number = "2", rowNumber = 2)
        startCollecting(viewModel)
        assertEquals("Week", viewModel.ready().rowTitle)
        assertEquals(2, viewModel.ready().rowNumber)
        assertFalse(viewModel.ready().canDelete)

        viewModel.type("income", "12.50")
        viewModel.save()

        assertEquals(
            listOf(TrackerEntry(1, "personal-fitness", "2", 2, mapOf("income" to "12.50"))),
            entries()
        )
    }

    @Test
    fun filledFixedRow_startsWithItsValues_andSaveChangesIt() = runTest {
        progressRepository.addTrackerEntry(
            "personal-fitness",
            "2",
            3,
            mapOf("income" to "10"),
            badgeStart
        )
        val viewModel = viewModel(number = "2", rowNumber = 3)
        startCollecting(viewModel)
        assertEquals("10", viewModel.text("income"))
        assertTrue(viewModel.ready().canDelete)

        viewModel.type("notes", "Mowed a lawn.")
        viewModel.save()

        assertEquals(
            listOf(3 to mapOf("income" to "10", "notes" to "Mowed a lawn.")),
            entries().map { it.rowNumber to it.values }
        )
    }

    @Test
    fun delete_removesTheEntry_thenIsDone() = runTest {
        val keep = addSession(mapOf("activity" to "Run"))
        val id = addSession(mapOf("activity" to "Swim"))
        val viewModel = viewModel(entryId = id)
        startCollecting(viewModel)

        viewModel.delete()

        assertEquals(listOf(keep), entries().map { it.id })
        assertTrue(viewModel.ready().done)
    }

    @Test
    fun delete_newEntry_doesNothing() = runTest {
        addSession(mapOf("activity" to "Run"))
        val viewModel = viewModel()
        startCollecting(viewModel)

        viewModel.delete()

        assertEquals(1, entries().size)
        assertFalse(viewModel.ready().done)
    }

    @Test
    fun setDate_setsAndRemovesIt() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)

        viewModel.setDate("date", LocalDate.of(2026, 5, 1))
        assertEquals(mapOf("date" to LocalDate.of(2026, 5, 1)), viewModel.ready().dates)
        assertTrue(viewModel.ready().canSave)

        viewModel.setDate("date", null)
        assertEquals(emptyMap<String, LocalDate>(), viewModel.ready().dates)
        assertFalse(viewModel.ready().canSave)
    }

    @Test
    fun logEntryNotInTheLog_isUnavailable() = runTest {
        val viewModel = viewModel(entryId = 99)
        startCollecting(viewModel)

        assertEquals(TrackerEntryUiState.Unavailable, viewModel.uiState.value)
    }

    @Test
    fun rowOutsideAFixedRowTracker_isUnavailable() = runTest {
        for (rowNumber in listOf(0, 4, null)) {
            val viewModel = viewModel(number = "2", rowNumber = rowNumber)
            startCollecting(viewModel)

            assertEquals(TrackerEntryUiState.Unavailable, viewModel.uiState.value)
        }
    }

    @Test
    fun requirementWithoutTracker_isUnavailable() = runTest {
        for (number in listOf("1", "99")) {
            val viewModel = viewModel(number = number)
            startCollecting(viewModel)

            assertEquals(TrackerEntryUiState.Unavailable, viewModel.uiState.value)
        }
    }

    @Test
    fun badgeMissingFromCatalog_isUnavailable() = runTest {
        val viewModel = viewModel(catalog = FakeCatalogRepository(emptyList()))
        startCollecting(viewModel)

        assertEquals(TrackerEntryUiState.Unavailable, viewModel.uiState.value)
    }

    @Test
    fun save_whenSaveFails_reportsIt_andKeepsTheEdit() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)
        viewModel.type("activity", "Run")
        progressRepository.failSaves = true

        viewModel.save()

        val failure = viewModel.ready().saveFailure
        assertNotNull(failure)
        assertFalse(viewModel.ready().done)
        assertEquals("Run", viewModel.text("activity"))
        // The scout can try again.
        assertTrue(viewModel.ready().canSave)

        viewModel.onSaveFailureShown(failure!!)

        assertNull(viewModel.ready().saveFailure)
    }

    @Test
    fun delete_whenSaveFails_reportsIt() = runTest {
        val id = addSession(mapOf("activity" to "Run"))
        val viewModel = viewModel(entryId = id)
        startCollecting(viewModel)
        progressRepository.failSaves = true

        viewModel.delete()

        assertNotNull(viewModel.ready().saveFailure)
        assertFalse(viewModel.ready().done)
        assertEquals(1, entries().size)
    }

    @Test
    fun unsavedValues_areRestoredFromSavedState() = runTest {
        val id = addSession(mapOf("activity" to "Run"))
        scenario(id).use { scenario ->
            startCollecting(scenario.viewModel)
            scenario.viewModel.type("activity", "Run, then edited.")
            scenario.viewModel.setDate("date", LocalDate.of(2026, 5, 1))

            scenario.recreate()
            val restored = scenario.viewModel
            startCollecting(restored)

            assertEquals("Run, then edited.", restored.text("activity"))
            assertEquals(mapOf("date" to LocalDate.of(2026, 5, 1)), restored.ready().dates)
            assertTrue(restored.ready().canSave)
        }
    }

    @Test
    fun appStoppedBeforeTheValuesLoad_loadsTheSavedValuesAgain() = runTest {
        val id = addSession(mapOf("activity" to "Run"))
        scenario(id).use { scenario ->
            // Nothing has collected uiState, so the saved values haven't loaded.
            assertEquals(emptyMap<String, Any>(), scenario.viewModel.fields)

            scenario.recreate()
            val restored = scenario.viewModel
            startCollecting(restored)

            assertEquals("Run", restored.text("activity"))
            assertFalse(restored.ready().canSave)
        }
    }
}
