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
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import io.github.bryancassell.bluecard.testing.MainDispatcherRule
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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
// plain local tests (see ARCHITECTURE.md, ViewModel tests).
@RunWith(AndroidJUnit4::class)
class RequirementDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val older = LocalDate.of(2025, 1, 1)
    private val newest = LocalDate.of(2026, 1, 1)
    private val started = LocalDate.of(2026, 3, 1)
    private val day = LocalDate.of(2026, 4, 15)
    private val badgeStart = BadgeStart(newest, started)
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
                                    listOf(TrackerColumn("night", "Night", TrackerColumnType.DATE)),
                                    "night",
                                    "nights"
                                )
                            )
                        )
                    ),
                    Requirement(
                        "4",
                        "Save for two weeks.",
                        tracker = TrackerDefinition(
                            listOf(TrackerColumn("saved", "Saved", TrackerColumnType.NUMBER)),
                            "week",
                            "weeks",
                            rowCount = 2
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
    private fun viewModel(
        number: String,
        catalog: CatalogRepository = catalogRepository,
        progress: ProgressRepository = progressRepository
    ) = RequirementDetailViewModel("camping", number, catalog, progress, clock, savedStateHandle)

    /**
     * Can save the ViewModel's state and restore it into a new one, as when the system stops
     * the app.
     */
    private fun scenario(number: String, catalog: CatalogRepository = catalogRepository) =
        viewModelScenario {
            RequirementDetailViewModel(
                "camping",
                number,
                catalog,
                progressRepository,
                clock,
                createSavedStateHandle()
            )
        }

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
        val viewModel = viewModel("2", loading)
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
                requirement = RequirementItem(
                    "2",
                    "Do two of these.",
                    Choice(2, 3),
                    false,
                    markedByHand = false
                ),
                completedDate = null,
                children = listOf(
                    RequirementItem("2a", "Cook a meal.", null, false, markedByHand = true),
                    RequirementItem(
                        "2b",
                        "Lead one hike.",
                        Choice(1, 2),
                        false,
                        markedByHand = false
                    ),
                    RequirementItem(
                        "2c",
                        "Keep a camping log.",
                        null,
                        false,
                        markedByHand = true,
                        TrackerCount(0, null, "nights")
                    )
                ),
                tracker = null,
                commentChanged = false,
                canClear = false,
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
                RequirementItem("2b(1)", "A day hike.", null, false, markedByHand = true),
                RequirementItem("2b(2)", "A night hike.", null, false, markedByHand = true)
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

        progressRepository.markRequirementCompleted("camping", "2a", day, badgeStart)
        assertTrue(viewModel.ready().children.single { it.number == "2a" }.completed)
        assertFalse(viewModel.ready().requirement.completed)

        // One of 2b's two choices completes 2b, and with 2a that's two of three.
        progressRepository.markRequirementCompleted("camping", "2b(2)", day, badgeStart)
        assertTrue(viewModel.ready().children.single { it.number == "2b" }.completed)
        assertTrue(viewModel.ready().requirement.completed)

        progressRepository.markRequirementNotCompleted("camping", "2a")
        assertFalse(viewModel.ready().requirement.completed)
    }

    @Test
    fun choiceLeftOnceEnoughAreComplete_isNotNeeded() = runTest {
        progressRepository.startBadge("camping", newest, started)
        val viewModel = viewModel("2b")
        startCollecting(viewModel)
        assertEquals(listOf(false, false), viewModel.ready().children.map { it.notNeeded })

        // 2b needs one of its two choices.
        progressRepository.markRequirementCompleted("camping", "2b(2)", day, badgeStart)

        assertEquals(listOf(true, false), viewModel.ready().children.map { it.notNeeded })
    }

    @Test
    fun requirementPartOfCompletedOne_isNotNeeded_andSoAreItsSubRequirements() = runTest {
        progressRepository.startBadge("camping", newest, started)
        val viewModel = viewModel("2b")
        startCollecting(viewModel)

        // 2a and 2c are two of 2's three, so 2 is complete without 2b.
        progressRepository.markRequirementCompleted("camping", "2a", day, badgeStart)
        progressRepository.markRequirementCompleted("camping", "2c", day, badgeStart)
        assertTrue(viewModel.ready().requirement.notNeeded)
        assertEquals(listOf(true, true), viewModel.ready().children.map { it.notNeeded })

        progressRepository.markRequirementNotCompleted("camping", "2a")
        assertFalse(viewModel.ready().requirement.notNeeded)
        assertEquals(listOf(false, false), viewModel.ready().children.map { it.notNeeded })
    }

    @Test
    fun requirementWithTracker_showsItsEntries() = runTest {
        progressRepository.addTrackerEntry(
            "camping",
            "2c",
            null,
            mapOf("night" to "2026-04-10"),
            today,
            badgeStart
        )
        val viewModel = viewModel("2c")
        startCollecting(viewModel)

        assertEquals(
            TrackerItem(
                count = TrackerCount(1, null, "night"),
                rowTitle = "Night",
                rowLabel = "night",
                rows = listOf(
                    TrackerRow(1, 1, listOf(TrackerValue(TrackerColumnType.DATE, "2026-04-10")))
                )
            ),
            viewModel.ready().tracker
        )
    }

    @Test
    fun tracker_updatesAsEntriesAreAdded() = runTest {
        val viewModel = viewModel("2c")
        startCollecting(viewModel)
        assertEquals(emptyList<TrackerRow>(), viewModel.ready().tracker!!.rows)

        progressRepository.addTrackerEntry(
            "camping",
            "2c",
            null,
            mapOf("night" to "2026-04-10"),
            today,
            badgeStart
        )

        assertEquals(TrackerCount(1, null, "night"), viewModel.ready().tracker!!.count)
    }

    @Test
    fun fixedRows_completeTheRequirementOnceEveryRowIsFilledIn() = runTest {
        val viewModel = viewModel("4")
        startCollecting(viewModel)
        assertFalse(viewModel.ready().requirement.markedByHand)

        val week1 = saveWeek(1, day)
        assertFalse(viewModel.ready().requirement.completed)
        saveWeek(2, today)
        assertTrue(viewModel.ready().requirement.completed)

        // Deleting a row makes it incomplete again.
        progressRepository.deleteTrackerEntry(week1)
        assertFalse(viewModel.ready().requirement.completed)
    }

    private suspend fun saveWeek(rowNumber: Int, addedDate: LocalDate) =
        progressRepository.addTrackerEntry(
            "camping",
            "4",
            rowNumber,
            mapOf("saved" to "5"),
            addedDate,
            badgeStart
        )

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

        viewModel.setCompleted(true)

        assertEquals(
            BadgeProgress("camping", newest, today),
            progressRepository.observeProgress("camping").first()!!.badge
        )
        assertTrue(viewModel.ready().requirement.completed)
        assertEquals(today, viewModel.ready().completedDate)
    }

    @Test
    fun setCompleted_onBadgeStartedOnOlderVersion_keepsThatVersion() = runTest {
        progressRepository.startBadge("camping", older, started)
        val viewModel = viewModel("2a")
        startCollecting(viewModel)

        viewModel.setCompleted(true)

        assertEquals(
            BadgeProgress("camping", older, started),
            progressRepository.observeProgress("camping").first()!!.badge
        )
        assertTrue(viewModel.ready().requirement.completed)
    }

    @Test
    fun setCompleted_false_undoesItAndRemovesDate() = runTest {
        val viewModel = viewModel("1")
        startCollecting(viewModel)
        viewModel.setCompleted(true)

        viewModel.setCompleted(false)

        assertFalse(viewModel.ready().requirement.completed)
        assertNull(viewModel.ready().completedDate)
    }

    @Test
    fun setCompletedDate_changesTheDate() = runTest {
        val viewModel = viewModel("1")
        startCollecting(viewModel)
        viewModel.setCompleted(true)

        viewModel.setCompletedDate(day)

        assertEquals(day, viewModel.ready().completedDate)
        assertTrue(viewModel.ready().requirement.completed)
    }

    @Test
    fun setCompletedDate_afterUnchecking_doesNotCompleteItAgain() = runTest {
        val viewModel = viewModel("1")
        startCollecting(viewModel)
        viewModel.setCompleted(true)

        // As when the scout taps Remove date just after unchecking, before the page redraws.
        viewModel.setCompleted(false)
        viewModel.setCompletedDate(null)

        assertFalse(viewModel.ready().requirement.completed)
    }

    @Test
    fun uncheckingThenChecking_onThisPage_keepsTheDate() = runTest {
        val viewModel = viewModel("1")
        startCollecting(viewModel)
        viewModel.setCompleted(true)
        viewModel.setCompletedDate(day)

        viewModel.setCompleted(false)
        viewModel.setCompleted(true)

        assertEquals(day, viewModel.ready().completedDate)
    }

    @Test
    fun setCompletedDate_null_removesTheDateButStaysCompleted() = runTest {
        val viewModel = viewModel("1")
        startCollecting(viewModel)
        viewModel.setCompleted(true)

        viewModel.setCompletedDate(null)

        assertNull(viewModel.ready().completedDate)
        assertTrue(viewModel.ready().requirement.completed)
    }

    @Test
    fun comment_startsAsSavedComment() = runTest {
        progressRepository.startBadge("camping", newest, started)
        progressRepository.setRequirementComment(
            "camping",
            "1",
            "Planned it with my patrol.",
            badgeStart
        )
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
        progressRepository.setRequirementComment(
            "camping",
            "1",
            "Planned it with my patrol.",
            badgeStart
        )
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

        viewModel.setCompleted(true)

        assertEquals("Not saved yet.", viewModel.comment.text.toString())
        assertTrue(viewModel.ready().commentChanged)
    }

    @Test
    fun unsavedComment_isRestoredFromSavedState() = runTest {
        progressRepository.startBadge("camping", newest, started)
        progressRepository.setRequirementComment("camping", "1", "Saved.", badgeStart)
        scenario("1").use { scenario ->
            startCollecting(scenario.viewModel)
            scenario.viewModel.typeComment("Saved, then edited.")

            scenario.recreate()
            val restored = scenario.viewModel
            startCollecting(restored)

            assertEquals("Saved, then edited.", restored.comment.text.toString())
            assertTrue(restored.ready().commentChanged)
        }
    }

    @Test
    fun restoredComment_isKeptAgain_evenWhileNothingCollects() = runTest {
        scenario("1").use { scenario ->
            startCollecting(scenario.viewModel)
            scenario.viewModel.typeComment("First edit.")
            scenario.recreate()

            // The restored ViewModel, before anything collects its uiState.
            scenario.viewModel.comment.setTextAndPlaceCursorAtEnd("Second edit.")
            scenario.recreate()
            val restored = scenario.viewModel
            startCollecting(restored)

            assertEquals("Second edit.", restored.comment.text.toString())
        }
    }

    @Test
    fun appStoppedBeforeTheCommentLoads_loadsTheSavedCommentAgain() = runTest {
        progressRepository.startBadge("camping", newest, started)
        progressRepository.setRequirementComment("camping", "1", "Saved.", badgeStart)
        scenario("1").use { scenario ->
            // Nothing has collected uiState, so the saved comment hasn't loaded.
            assertEquals("", scenario.viewModel.comment.text.toString())

            scenario.recreate()
            val restored = scenario.viewModel
            startCollecting(restored)

            assertEquals("Saved.", restored.comment.text.toString())
            assertFalse(restored.ready().commentChanged)
        }
    }

    @Test
    fun anotherValueUnderTheCommentKey_loadsTheSavedComment() = runTest {
        progressRepository.startBadge("camping", newest, started)
        progressRepository.setRequirementComment("camping", "1", "Saved.", badgeStart)
        // A value keepText didn't keep.
        val viewModel = RequirementDetailViewModel(
            "camping",
            "1",
            catalogRepository,
            progressRepository,
            clock,
            SavedStateHandle(mapOf("comment" to "From an intent."))
        )
        startCollecting(viewModel)

        assertEquals("Saved.", viewModel.comment.text.toString())
        assertFalse(viewModel.ready().commentChanged)
    }

    @Test
    fun setCompleted_whenSaveFails_reportsItUntilShown() = runTest {
        val viewModel = viewModel("1")
        startCollecting(viewModel)
        progressRepository.failSaves = true

        viewModel.setCompleted(true)

        val failure = viewModel.ready().saveFailure
        assertNotNull(failure)
        assertFalse(viewModel.ready().requirement.completed)
        // Starting the badge failed with the rest.
        progressRepository.failSaves = false
        assertNull(progressRepository.observeProgress("camping").first())

        viewModel.onSaveFailureShown(failure!!)

        assertNull(viewModel.ready().saveFailure)
    }

    @Test
    fun setCompletedDate_whenSaveFails_reportsIt() = runTest {
        val viewModel = viewModel("1")
        startCollecting(viewModel)
        viewModel.setCompleted(true)
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

    @Test
    fun canClear_onlyWhileSomethingShowsAsRecorded() = runTest {
        val viewModel = viewModel("1")
        startCollecting(viewModel)
        assertFalse(viewModel.ready().canClear)

        viewModel.setCompleted(true)
        assertTrue(viewModel.ready().canClear)

        // Unchecked, it has nothing left that shows.
        viewModel.setCompleted(false)
        assertFalse(viewModel.ready().canClear)
    }

    @Test
    fun canClear_whenARequirementUnderItHasProgress() = runTest {
        val viewModel = viewModel("2")
        startCollecting(viewModel)

        progressRepository.markRequirementCompleted("camping", "2b(1)", day, badgeStart)

        assertTrue(viewModel.ready().canClear)
    }

    @Test
    fun clear_removesWhatsRecordedForItAndThoseUnderIt_andNothingElse() = runTest {
        progressRepository.markRequirementCompleted("camping", "1", day, badgeStart)
        progressRepository.setRequirementComment("camping", "2", "Chose 2a and 2c.", badgeStart)
        progressRepository.markRequirementCompleted("camping", "2a", day, badgeStart)
        progressRepository.markRequirementCompleted("camping", "2b(1)", day, badgeStart)
        progressRepository.addTrackerEntry(
            "camping",
            "2c",
            null,
            mapOf("night" to "2026-04-11"),
            day,
            badgeStart
        )
        val viewModel = viewModel("2")
        startCollecting(viewModel)

        viewModel.clear()

        val progress = progressRepository.observeProgress("camping").first()!!
        assertEquals(listOf("1"), progress.requirements.map { it.requirementNumber })
        assertEquals(emptyList<Any>(), progress.trackerEntries)
        // The badge stays started.
        assertEquals(BadgeProgress("camping", newest, started), progress.badge)
        assertFalse(viewModel.ready().canClear)
        assertTrue(viewModel.ready().children.none { it.completed })
    }

    /**
     * A catalog that takes a moment to read, as the real one can, so a clear that read anything
     * first would wait for it.
     */
    private val slowCatalog = object : CatalogRepository {
        override suspend fun getBadges(): List<MeritBadge> {
            delay(100)
            return listOf(camping)
        }
    }

    @Test
    fun clear_thenCheckingStraightAway_keepsTheCheck() = runTest {
        progressRepository.setRequirementComment("camping", "1", "Saved.", badgeStart)
        val viewModel = viewModel("1", slowCatalog)
        startCollecting(viewModel)
        advanceUntilIdle()

        // As when the scout confirms Clear, then checks the box before the page redraws: the
        // clear is made first, as it was asked for first.
        viewModel.clear()
        viewModel.setCompleted(true)
        advanceUntilIdle()

        assertEquals(RequirementProgress("camping", "1", true, today), recorded("1"))
    }

    @Test
    fun clear_thenLeavingThePage_stillClears() = runTest {
        progressRepository.setRequirementComment("camping", "1", "Saved.", badgeStart)
        scenario("1", slowCatalog).use { scenario ->
            startCollecting(scenario.viewModel)
            advanceUntilIdle()

            scenario.viewModel.clear()
            // The page closes straight away, which cancels the ViewModel's coroutines.
        }
        advanceUntilIdle()

        assertNull(recorded("1"))
    }

    @Test
    fun clear_emptiesTheCommentField() = runTest {
        progressRepository.setRequirementComment("camping", "1", "Saved.", badgeStart)
        val viewModel = viewModel("1")
        startCollecting(viewModel)

        viewModel.clear()

        assertNull(recorded("1"))
        assertEquals("", viewModel.comment.text.toString())
        assertFalse(viewModel.ready().commentChanged)
    }

    @Test
    fun clear_discardsAnUnsavedEdit() = runTest {
        progressRepository.setRequirementComment("camping", "1", "Saved.", badgeStart)
        val viewModel = viewModel("1")
        startCollecting(viewModel)
        viewModel.typeComment("Saved, then edited.")

        viewModel.clear()

        assertNull(recorded("1"))
        assertEquals("", viewModel.comment.text.toString())
        assertFalse(viewModel.ready().commentChanged)
    }

    @Test
    fun clear_discardsAnUnsavedEdit_whenNoCommentIsSaved() = runTest {
        progressRepository.markRequirementCompleted("camping", "1", day, badgeStart)
        val viewModel = viewModel("1")
        startCollecting(viewModel)
        viewModel.typeComment("Not saved.")

        viewModel.clear()

        assertNull(recorded("1"))
        assertEquals("", viewModel.comment.text.toString())
        assertFalse(viewModel.ready().commentChanged)
    }

    private val clearSaved = CompletableDeferred<Unit>()

    /** Saves a clear only once [clearSaved] lets it through, as Room takes a moment to. */
    private val slowClears = object : ProgressRepository by progressRepository {
        override suspend fun clearRequirements(badgeId: String, numbers: Collection<String>) {
            clearSaved.await()
            progressRepository.clearRequirements(badgeId, numbers)
        }
    }

    @Test
    fun clear_whileBeingSaved_keepsTheUnsavedEdit_untilItIsSaved() = runTest {
        progressRepository.setRequirementComment("camping", "1", "Saved.", badgeStart)
        val viewModel = viewModel("1", progress = slowClears)
        startCollecting(viewModel)
        viewModel.typeComment("Saved, then edited.")

        viewModel.clear()

        assertEquals("Saved, then edited.", viewModel.comment.text.toString())
        assertTrue(viewModel.ready().commentChanged)
        clearSaved.complete(Unit)
        advanceUntilIdle()

        assertNull(recorded("1"))
        assertEquals("", viewModel.comment.text.toString())
        assertFalse(viewModel.ready().commentChanged)
    }

    @Test
    fun clear_keepsWhatsTypedWhileItIsBeingSaved() = runTest {
        progressRepository.setRequirementComment("camping", "1", "Saved.", badgeStart)
        val viewModel = viewModel("1", progress = slowClears)
        startCollecting(viewModel)
        viewModel.typeComment("Saved, then edited.")

        viewModel.clear()
        viewModel.typeComment("Typed after Clear.")
        clearSaved.complete(Unit)
        advanceUntilIdle()

        assertNull(recorded("1"))
        assertEquals("Typed after Clear.", viewModel.comment.text.toString())
        assertTrue(viewModel.ready().commentChanged)
    }

    @Test
    fun clear_whenSaveFails_reportsIt_andKeepsTheUnsavedEdit() = runTest {
        progressRepository.setRequirementComment("camping", "1", "Saved.", badgeStart)
        val viewModel = viewModel("1")
        startCollecting(viewModel)
        viewModel.typeComment("Saved, then edited.")
        progressRepository.failSaves = true

        viewModel.clear()

        assertNotNull(viewModel.ready().saveFailure)
        assertEquals("Saved.", recorded("1")?.comment)
        assertEquals("Saved, then edited.", viewModel.comment.text.toString())
        assertTrue(viewModel.ready().commentChanged)
        assertTrue(viewModel.ready().canClear)
    }

    @Test
    fun setCompleted_false_afterTheBadgeIsCleared_doesNothing() = runTest {
        val viewModel = viewModel("1")
        startCollecting(viewModel)
        viewModel.setCompleted(true)

        // As when the badge is cleared while the page still shows the requirement checked.
        progressRepository.clearBadge("camping")
        viewModel.setCompleted(false)

        assertNull(progressRepository.observeProgress("camping").first())
        assertNull(viewModel.ready().saveFailure)
    }
}
