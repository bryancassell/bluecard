package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
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
import io.github.bryancassell.bluecard.data.progress.FakeProgressRepository
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
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
class RequirementDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val older = LocalDate.of(2025, 1, 1)
    private val newest = LocalDate.of(2026, 1, 1)
    private val started = LocalDate.of(2026, 3, 1)
    private val day = LocalDate.of(2026, 4, 15)
    private val today = LocalDate.of(2026, 5, 20)
    private val clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    private val camping = MeritBadge(
        id = "camping",
        name = "Camping",
        summary = "Our summary of Camping.",
        officialUrl = "https://www.scouting.org/merit-badges/camping/",
        requirementVersions = listOf(
            RequirementsVersion(
                newest,
                listOf(
                    Requirement("1", "Plan a campout."),
                    Requirement(
                        "2",
                        "Do two of these.",
                        requiredCount = 2,
                        children = listOf(
                            Requirement("2a", "Cook a meal."),
                            Requirement(
                                "2b",
                                "Lead one hike.",
                                requiredCount = 1,
                                children = listOf(
                                    Requirement("2b(1)", "A day hike."),
                                    Requirement("2b(2)", "A night hike.")
                                )
                            ),
                            Requirement(
                                "2c",
                                "Keep a camping log.",
                                tracker = TrackerDefinition(
                                    listOf(TrackerColumn("night", "Night", TrackerColumnType.DATE))
                                )
                            )
                        )
                    )
                )
            ),
            RequirementsVersion(
                older,
                listOf(
                    Requirement(
                        "2",
                        "An older second requirement.",
                        children = listOf(Requirement("2a", "An older 2a."))
                    ),
                    Requirement(
                        "3",
                        "Only in the older version.",
                        children = listOf(Requirement("3a", "An older 3a."))
                    )
                )
            )
        )
    )

    private val catalogRepository = FakeCatalogRepository(listOf(camping))
    private val progressRepository = FakeProgressRepository()
    private val savedStateHandle = SavedStateHandle()

    // Created in the test, after MainDispatcherRule has replaced the Main dispatcher that
    // viewModelScope uses.
    private fun viewModel(number: String) = RequirementDetailViewModel(
        "camping",
        number,
        catalogRepository,
        progressRepository,
        clock,
        savedStateHandle
    )

    /**
     * Collects uiState, as the screen does, so WhileSubscribed starts it. From the
     * coroutines testing guide: https://developer.android.com/kotlin/coroutines/test#statein
     */
    private fun TestScope.startCollecting(viewModel: RequirementDetailViewModel) {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }
    }

    private fun RequirementDetailViewModel.ready() = uiState.value as RequirementDetailUiState.Ready

    @Test
    fun uiState_whileCatalogLoads_isLoading() = runTest {
        val loading = object : CatalogRepository {
            override suspend fun getBadges(): List<MeritBadge> = awaitCancellation()
        }
        val viewModel = RequirementDetailViewModel(
            "camping",
            "2",
            loading,
            progressRepository,
            clock,
            savedStateHandle
        )
        startCollecting(viewModel)

        assertEquals(RequirementDetailUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun uiState_whenCatalogCantBeRead_isLoadFailed() = runTest {
        catalogRepository.failLoads = true
        val viewModel = viewModel("2")
        startCollecting(viewModel)

        assertEquals(RequirementDetailUiState.LoadFailed, viewModel.uiState.value)
    }

    @Test
    fun uiState_whenProgressCantBeRead_isLoadFailed() = runTest {
        progressRepository.failLoads = true
        val viewModel = viewModel("2")
        startCollecting(viewModel)

        assertEquals(RequirementDetailUiState.LoadFailed, viewModel.uiState.value)
    }

    @Test
    fun showsRequirementWithItsSubRequirements() = runTest {
        val viewModel = viewModel("2")
        startCollecting(viewModel)

        assertEquals(
            RequirementDetailUiState.Ready(
                badgeName = "Camping",
                requirement = RequirementItem("2", "Do two of these.", Choice(2, 3), false, true),
                completedDate = null,
                children = listOf(
                    RequirementItem("2a", "Cook a meal.", null, false, false),
                    RequirementItem("2b", "Lead one hike.", Choice(1, 2), false, true),
                    RequirementItem("2c", "Keep a camping log.", null, false, false)
                ),
                commentChanged = false,
                today = today,
                saveFailure = null
            ),
            viewModel.uiState.value
        )
    }

    @Test
    fun nestedRequirement_showsItsSubRequirements() = runTest {
        val viewModel = viewModel("2b")
        startCollecting(viewModel)

        assertEquals("Lead one hike.", viewModel.ready().requirement.summary)
        assertEquals(
            listOf(
                RequirementItem("2b(1)", "A day hike.", null, false, false),
                RequirementItem("2b(2)", "A night hike.", null, false, false)
            ),
            viewModel.ready().children
        )
    }

    @Test
    fun startedOnOlderVersion_showsThatVersionsRequirement() = runTest {
        progressRepository.startBadge("camping", older, started)
        val viewModel = viewModel("2")
        startCollecting(viewModel)

        assertEquals("An older second requirement.", viewModel.ready().requirement.summary)
        assertEquals(listOf("An older 2a."), viewModel.ready().children.map { it.summary })
    }

    @Test
    fun requirementMissingFromVersion_isUnavailable() = runTest {
        // Requirement 3 is only in the older version, and the badge hasn't been started.
        val viewModel = viewModel("3")
        startCollecting(viewModel)

        assertEquals(RequirementDetailUiState.Unavailable, viewModel.uiState.value)
    }

    @Test
    fun requirementGoneAfterProgressChanges_isUnavailable() = runTest {
        progressRepository.startBadge("camping", older, started)
        val viewModel = viewModel("3")
        startCollecting(viewModel)
        assertEquals("Only in the older version.", viewModel.ready().requirement.summary)

        // Clearing the badge puts it back on the newest version, which has no 3.
        progressRepository.clearBadge("camping")

        assertEquals(RequirementDetailUiState.Unavailable, viewModel.uiState.value)
    }

    @Test
    fun startedOnVersionMissingFromCatalog_isUnavailable() = runTest {
        progressRepository.startBadge("camping", LocalDate.of(2024, 1, 1), started)
        val viewModel = viewModel("2")
        startCollecting(viewModel)

        assertEquals(RequirementDetailUiState.Unavailable, viewModel.uiState.value)
    }

    @Test
    fun badgeMissingFromCatalog_isUnavailable() = runTest {
        val viewModel = RequirementDetailViewModel(
            "retired-badge",
            "2",
            catalogRepository,
            progressRepository,
            clock,
            savedStateHandle
        )
        startCollecting(viewModel)

        assertEquals(RequirementDetailUiState.Unavailable, viewModel.uiState.value)
    }

    @Test
    fun completion_updatesAsSubRequirementsAreCompleted() = runTest {
        progressRepository.startBadge("camping", newest, started)
        val viewModel = viewModel("2")
        startCollecting(viewModel)

        progressRepository.markRequirementCompleted("camping", "2a", day)
        assertTrue(viewModel.ready().children.single { it.number == "2a" }.completed)
        assertFalse(viewModel.ready().requirement.completed)

        // One of 2b's two choices completes 2b, and with 2a that's two of three.
        progressRepository.markRequirementCompleted("camping", "2b(2)", day)
        assertTrue(viewModel.ready().children.single { it.number == "2b" }.completed)
        assertTrue(viewModel.ready().requirement.completed)
    }

    private suspend fun recorded(number: String) = progressRepository.observeProgress("camping")
        .first()?.requirements?.singleOrNull { it.requirementNumber == number }

    /** Types into the comment field, as the scout does. */
    private fun RequirementDetailViewModel.typeComment(text: String) {
        comment.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun today_isFromTheClock() = runTest {
        val viewModel = viewModel("1")
        startCollecting(viewModel)

        assertEquals(today, viewModel.ready().today)
    }

    @Test
    fun setCompleted_onUnstartedBadge_startsItAndCompletesThisRequirementToday() = runTest {
        val viewModel = viewModel("1")
        startCollecting(viewModel)
        assertFalse(viewModel.ready().requirement.completed)
        assertNull(viewModel.ready().completedDate)

        viewModel.setCompleted("1", true)

        assertEquals(
            BadgeProgress("camping", newest, today),
            progressRepository.observeProgress("camping").first()!!.badge
        )
        assertTrue(viewModel.ready().requirement.completed)
        assertEquals(today, viewModel.ready().completedDate)
    }

    @Test
    fun setCompleted_false_undoesItAndRemovesDate() = runTest {
        val viewModel = viewModel("1")
        startCollecting(viewModel)
        viewModel.setCompleted("1", true)

        viewModel.setCompleted("1", false)

        assertFalse(viewModel.ready().requirement.completed)
        assertNull(viewModel.ready().completedDate)
    }

    @Test
    fun completingEnoughSubRequirements_completesThisRequirement() = runTest {
        val viewModel = viewModel("2")
        startCollecting(viewModel)

        viewModel.setCompleted("2a", true)
        assertTrue(viewModel.ready().children.single { it.number == "2a" }.completed)
        assertFalse(viewModel.ready().requirement.completed)

        viewModel.setCompleted("2c", true)
        assertTrue(viewModel.ready().requirement.completed)

        viewModel.setCompleted("2a", false)
        assertFalse(viewModel.ready().requirement.completed)
    }

    @Test
    fun setCompletedDate_changesTheDate() = runTest {
        val viewModel = viewModel("1")
        startCollecting(viewModel)
        viewModel.setCompleted("1", true)

        viewModel.setCompletedDate(day)

        assertEquals(day, viewModel.ready().completedDate)
        assertTrue(viewModel.ready().requirement.completed)
    }

    @Test
    fun setCompletedDate_afterUnchecking_doesNotCompleteItAgain() = runTest {
        val viewModel = viewModel("1")
        startCollecting(viewModel)
        viewModel.setCompleted("1", true)

        // As when the scout taps Remove date just after unchecking, before the page redraws.
        viewModel.setCompleted("1", false)
        viewModel.setCompletedDate(null)

        assertFalse(viewModel.ready().requirement.completed)
    }

    @Test
    fun uncheckingThenChecking_onThisPage_keepsTheDate() = runTest {
        val viewModel = viewModel("1")
        startCollecting(viewModel)
        viewModel.setCompleted("1", true)
        viewModel.setCompletedDate(day)

        viewModel.setCompleted("1", false)
        viewModel.setCompleted("1", true)

        assertEquals(day, viewModel.ready().completedDate)
    }

    @Test
    fun setCompletedDate_null_removesTheDateButStaysCompleted() = runTest {
        val viewModel = viewModel("1")
        startCollecting(viewModel)
        viewModel.setCompleted("1", true)

        viewModel.setCompletedDate(null)

        assertNull(viewModel.ready().completedDate)
        assertTrue(viewModel.ready().requirement.completed)
    }

    @Test
    fun comment_startsAsSavedComment() = runTest {
        progressRepository.startBadge("camping", newest, started)
        progressRepository.setRequirementComment("camping", "1", "Planned it with my patrol.")
        val viewModel = viewModel("1")
        startCollecting(viewModel)

        assertEquals("Planned it with my patrol.", viewModel.comment.text.toString())
        assertFalse(viewModel.ready().commentChanged)
    }

    @Test
    fun comment_edited_isChanged_untilSaved() = runTest {
        val viewModel = viewModel("1")
        startCollecting(viewModel)
        assertEquals("", viewModel.comment.text.toString())

        viewModel.typeComment("Planned it with my patrol.")
        assertTrue(viewModel.ready().commentChanged)

        viewModel.saveComment()

        assertFalse(viewModel.ready().commentChanged)
        assertEquals("Planned it with my patrol.", recorded("1")?.comment)
    }

    @Test
    fun saveComment_onUnstartedBadge_startsItWithoutCompletingTheRequirement() = runTest {
        val viewModel = viewModel("1")
        startCollecting(viewModel)
        viewModel.typeComment("Next week.")

        viewModel.saveComment()

        assertEquals(
            BadgeProgress("camping", newest, today),
            progressRepository.observeProgress("camping").first()!!.badge
        )
        assertEquals(RequirementProgress("camping", "1", comment = "Next week."), recorded("1"))
    }

    @Test
    fun saveComment_trimsSpaces_andOnlySpacesAreNoChange() = runTest {
        val viewModel = viewModel("1")
        startCollecting(viewModel)

        viewModel.typeComment("   ")
        assertFalse(viewModel.ready().commentChanged)

        viewModel.typeComment("  Done at camp.  ")
        viewModel.saveComment()

        assertEquals("Done at camp.", recorded("1")?.comment)
        assertFalse(viewModel.ready().commentChanged)
    }

    @Test
    fun saveComment_empty_removesIt() = runTest {
        progressRepository.startBadge("camping", newest, started)
        progressRepository.setRequirementComment("camping", "1", "Planned it with my patrol.")
        val viewModel = viewModel("1")
        startCollecting(viewModel)

        viewModel.typeComment("")
        assertTrue(viewModel.ready().commentChanged)
        viewModel.saveComment()

        assertNull(recorded("1")?.comment)
        assertFalse(viewModel.ready().commentChanged)
    }

    @Test
    fun comment_onRequirementWithSubRequirements_isSavedForIt() = runTest {
        val viewModel = viewModel("2")
        startCollecting(viewModel)

        viewModel.typeComment("Chose 2a and 2c.")
        viewModel.saveComment()

        assertEquals(
            RequirementProgress("camping", "2", comment = "Chose 2a and 2c."),
            recorded("2")
        )
    }

    @Test
    fun unsavedComment_isKeptWhenProgressChanges() = runTest {
        val viewModel = viewModel("1")
        startCollecting(viewModel)
        viewModel.typeComment("Not saved yet.")

        viewModel.setCompleted("1", true)

        assertEquals("Not saved yet.", viewModel.comment.text.toString())
        assertTrue(viewModel.ready().commentChanged)
    }

    @Test
    fun unsavedComment_isRestoredFromSavedState() = runTest {
        progressRepository.startBadge("camping", newest, started)
        progressRepository.setRequirementComment("camping", "1", "Saved.")
        val viewModel = viewModel("1")
        startCollecting(viewModel)
        viewModel.typeComment("Saved, then edited.")

        // A new ViewModel with the same saved state, as after the system stopped the app.
        val restored = viewModel("1")
        startCollecting(restored)

        assertEquals("Saved, then edited.", restored.comment.text.toString())
        assertTrue(restored.ready().commentChanged)
    }

    @Test
    fun setCompleted_whenSaveFails_reportsItUntilShown() = runTest {
        val viewModel = viewModel("1")
        startCollecting(viewModel)
        progressRepository.failSaves = true

        viewModel.setCompleted("1", true)

        val failure = viewModel.ready().saveFailure
        assertNotNull(failure)
        assertFalse(viewModel.ready().requirement.completed)

        viewModel.onSaveFailureShown(failure!!)

        assertNull(viewModel.ready().saveFailure)
    }

    @Test
    fun setCompletedDate_whenSaveFails_reportsIt() = runTest {
        val viewModel = viewModel("1")
        startCollecting(viewModel)
        viewModel.setCompleted("1", true)
        progressRepository.failSaves = true

        viewModel.setCompletedDate(day)

        assertNotNull(viewModel.ready().saveFailure)
        assertEquals(today, viewModel.ready().completedDate)
    }

    @Test
    fun saveComment_whenSaveFails_reportsIt_andKeepsTheEdit() = runTest {
        val viewModel = viewModel("1")
        startCollecting(viewModel)
        viewModel.typeComment("Not saved yet.")
        progressRepository.failSaves = true

        viewModel.saveComment()

        assertNotNull(viewModel.ready().saveFailure)
        assertEquals("Not saved yet.", viewModel.comment.text.toString())
        assertTrue(viewModel.ready().commentChanged)
    }
}
