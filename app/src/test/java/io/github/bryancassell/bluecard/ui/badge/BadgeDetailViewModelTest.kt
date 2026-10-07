package io.github.bryancassell.bluecard.ui.badge

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.testing.viewModelScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.catalog.FakeCatalogRepository
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.MeritBadgesNeeded
import io.github.bryancassell.bluecard.data.catalog.Rank
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition
import io.github.bryancassell.bluecard.data.progress.BadgeProgress
import io.github.bryancassell.bluecard.data.progress.BadgeStart
import io.github.bryancassell.bluecard.data.progress.BadgeStatus
import io.github.bryancassell.bluecard.data.progress.Counselor
import io.github.bryancassell.bluecard.data.progress.FakeProgressRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.report.FakeReportRepository
import io.github.bryancassell.bluecard.testing.FakeClock
import io.github.bryancassell.bluecard.testing.MainDispatcherRule
import io.github.bryancassell.bluecard.ui.badges.EagleRequirement
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
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
// plain local tests (see ARCHITECTURE.md, ViewModel tests).
@RunWith(AndroidJUnit4::class)
class BadgeDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val oldest = LocalDate.of(2024, 1, 1)
    private val older = LocalDate.of(2025, 1, 1)
    private val newest = LocalDate.of(2026, 1, 1)
    private val started = LocalDate.of(2026, 3, 1)
    private val day = LocalDate.of(2026, 4, 15)
    private val badgeStart = BadgeStart(newest, started)
    private val today = LocalDate.of(2026, 5, 20)
    private val clock = FakeClock(today.atTime(12, 0).toInstant(ZoneOffset.UTC))

    private val camping = MeritBadge(
        id = "camping",
        name = "Camping",
        summary = "Our summary of Camping.",
        officialUrl = "https://www.scouting.org/merit-badges/camping/",
        eagleRequired = true,
        // The newest version is neither the first nor the last one listed.
        requirementVersions = listOf(
            RequirementsVersion(older, listOf(Requirement("1", "An older first requirement."))),
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
                            Requirement("2b", "Lead a hike."),
                            Requirement("2c", "Pitch a tent.")
                        )
                    ),
                    Requirement(
                        "3",
                        "Keep a camping log.",
                        tracker = TrackerDefinition(
                            listOf(TrackerColumn("night", "Night", TrackerColumnType.DATE)),
                            "night",
                            "nights"
                        )
                    ),
                    Requirement(
                        "4",
                        "Do all of these.",
                        // Two of two is all of them, not a choice.
                        requiredCount = 2,
                        children = listOf(
                            Requirement("4a", "Pack a first aid kit."),
                            Requirement("4b", "Treat a blister.")
                        )
                    )
                )
            ),
            RequirementsVersion(oldest, listOf(Requirement("1", "The oldest first requirement.")))
        )
    )
    private val chess = MeritBadge(
        id = "chess",
        name = "Chess",
        summary = "Our summary of Chess.",
        officialUrl = "https://www.scouting.org/merit-badges/chess/",
        requirementVersions = listOf(RequirementsVersion(newest, listOf(Requirement("1", "Play."))))
    )

    private val catalogRepository = FakeCatalogRepository(listOf(chess, camping))
    private val progressRepository = FakeProgressRepository()
    private val reportRepository = FakeReportRepository(progressRepository)

    // Created in the test, after MainDispatcherRule has replaced the Main dispatcher that
    // viewModelScope uses.
    private fun viewModel(
        badgeId: String = "camping",
        progress: ProgressRepository = progressRepository,
        savedStateHandle: SavedStateHandle = SavedStateHandle()
    ) = BadgeDetailViewModel(
        badgeId,
        catalogRepository,
        progress,
        reportRepository,
        clock,
        savedStateHandle
    )

    /**
     * Collects uiState, as the screen does, so WhileSubscribed starts it. From the
     * coroutines testing guide: https://developer.android.com/kotlin/coroutines/test#statein
     */
    private fun TestScope.startCollecting(viewModel: BadgeDetailViewModel) {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }
    }

    private fun BadgeDetailViewModel.ready() = uiState.value as BadgeDetailUiState.Ready

    private fun BadgeDetailViewModel.completed() =
        ready().requirements.associate { it.number to it.completed }

    @Test
    fun uiState_whileCatalogLoads_isLoading() = runTest {
        val loading = object : CatalogRepository {
            override suspend fun getBadges(): List<MeritBadge> = awaitCancellation()
            override suspend fun getRanks(): List<Rank> = awaitCancellation()
        }
        val viewModel = BadgeDetailViewModel(
            "camping",
            loading,
            progressRepository,
            reportRepository,
            clock,
            SavedStateHandle()
        )
        startCollecting(viewModel)

        assertEquals(BadgeDetailUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun uiState_whenCatalogCantBeRead_isLoadFailed() = runTest {
        catalogRepository.failLoads = true
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals(BadgeDetailUiState.LoadFailed, viewModel.uiState.value)
    }

    @Test
    fun uiState_whenProgressCantBeRead_isLoadFailed() = runTest {
        progressRepository.failLoads = true
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals(BadgeDetailUiState.LoadFailed, viewModel.uiState.value)
    }

    @Test
    fun notStarted_showsBadgeWithNewestRequirements() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals(
            BadgeDetailUiState.Ready(
                name = "Camping",
                summary = "Our summary of Camping.",
                eagle = EagleRequirement.Required,
                officialUrl = "https://www.scouting.org/merit-badges/camping/",
                requirements = listOf(
                    RequirementItem("1", "Plan a campout.", null, false, markedByHand = true),
                    RequirementItem(
                        "2",
                        "Do two of these.",
                        Choice(2, 3),
                        false,
                        markedByHand = false
                    ),
                    // The scout marks a requirement with a log complete themselves.
                    RequirementItem(
                        "3",
                        "Keep a camping log.",
                        null,
                        false,
                        markedByHand = true,
                        TrackerCount(0, null, "nights")
                    ),
                    RequirementItem("4", "Do all of these.", null, false, markedByHand = false)
                )
            ),
            viewModel.uiState.value
        )
    }

    @Test
    fun startedOnOlderVersion_showsThatVersionsRequirements() = runTest {
        progressRepository.startBadge("camping", older, started)
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals(
            listOf(
                RequirementItem(
                    "1",
                    "An older first requirement.",
                    null,
                    false,
                    markedByHand = true
                )
            ),
            viewModel.ready().requirements
        )
    }

    @Test
    fun startedOnVersionMissingFromCatalog_isUnavailable() = runTest {
        progressRepository.startBadge("camping", LocalDate.of(2023, 1, 1), started)
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals(BadgeDetailUiState.Unavailable, viewModel.uiState.value)
    }

    @Test
    fun badgeMissingFromCatalog_isUnavailable() = runTest {
        val viewModel = viewModel("retired-badge")
        startCollecting(viewModel)

        assertEquals(BadgeDetailUiState.Unavailable, viewModel.uiState.value)
    }

    @Test
    fun notEagleRequired_hasNoEagleFlag() = runTest {
        val viewModel = viewModel("chess")
        startCollecting(viewModel)

        assertNull(viewModel.ready().eagle)
    }

    @Test
    fun eagleGroup_namesEveryBadgeInTheGroup() = runTest {
        fun eagleBadge(id: String, name: String) =
            chess.copy(id = id, name = name, eagleRequired = true, eagleGroup = "c-h-s")
        catalogRepository.badges = listOf(
            eagleBadge("swimming", "Swimming"),
            eagleBadge("cycling", "Cycling"),
            eagleBadge("hiking", "Hiking"),
            camping
        )
        val viewModel = viewModel("hiking")
        startCollecting(viewModel)

        assertEquals(
            EagleRequirement.OneOf(listOf("Cycling", "Hiking", "Swimming")),
            viewModel.ready().eagle
        )
    }

    @Test
    fun requirements_showWhetherEachIsComplete() = runTest {
        progressRepository.startBadge("camping", newest, started)
        progressRepository.markRequirementCompleted("camping", "1", day, badgeStart)
        // Two of three is enough for requirement 2.
        progressRepository.markRequirementCompleted("camping", "2a", day, badgeStart)
        progressRepository.markRequirementCompleted("camping", "2c", null, badgeStart)
        // One of two isn't enough for requirement 4.
        progressRepository.markRequirementCompleted("camping", "4a", day, badgeStart)
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals(
            mapOf("1" to true, "2" to true, "3" to false, "4" to false),
            viewModel.completed()
        )
    }

    @Test
    fun counselor_isShownOnceSaved() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)
        assertNull(viewModel.ready().counselor)

        progressRepository.setCounselor(
            "camping",
            Counselor("Pat Lee", "555-0100", "pat@example.com"),
            badgeStart
        )

        assertEquals(
            Counselor("Pat Lee", "555-0100", "pat@example.com"),
            viewModel.ready().counselor
        )
    }

    @Test
    fun uiState_updatesWhenProgressChanges() = runTest {
        progressRepository.startBadge("camping", newest, started)
        val viewModel = viewModel()
        startCollecting(viewModel)
        assertEquals(false, viewModel.completed()["1"])

        progressRepository.markRequirementCompleted("camping", "1", day, badgeStart)
        assertEquals(true, viewModel.completed()["1"])

        progressRepository.markRequirementNotCompleted("camping", "1")
        assertEquals(false, viewModel.completed()["1"])
    }

    @Test
    fun trackerCount_updatesAsEntriesAreAdded() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)

        progressRepository.addTrackerEntry(
            "camping",
            "3",
            null,
            mapOf("night" to "2026-04-10"),
            started,
            badgeStart
        )
        progressRepository.addTrackerEntry(
            "camping",
            "3",
            null,
            mapOf("night" to "2026-04-11"),
            started,
            badgeStart
        )

        assertEquals(TrackerCount(2, null, "nights"), viewModel.ready().requirements[2].tracker)
    }

    @Test
    fun clearingBadgeStartedOnOlderVersion_showsNewestRequirements() = runTest {
        progressRepository.startBadge("camping", older, started)
        val viewModel = viewModel()
        startCollecting(viewModel)
        assertEquals("An older first requirement.", viewModel.ready().requirements[0].summary)

        progressRepository.clearBadge("camping")

        assertEquals("Plan a campout.", viewModel.ready().requirements[0].summary)
    }

    /** Completes every requirement of Chess, a badge with one. */
    private suspend fun completeChess() {
        progressRepository.markRequirementCompleted("chess", "1", day, BadgeStart(newest, started))
    }

    @Test
    fun badgeNotStarted_isNotCompleted() = runTest {
        val viewModel = viewModel("chess")
        startCollecting(viewModel)

        assertEquals(BadgeStatus.NotStarted, viewModel.ready().status)
        assertFalse(viewModel.ready().completed)
    }

    @Test
    fun badgeWithEveryRequirementComplete_isCompleted() = runTest {
        val viewModel = viewModel("chess")
        startCollecting(viewModel)

        completeChess()

        assertTrue(viewModel.ready().completed)
    }

    @Test
    fun badgeWithSomeRequirementsComplete_isNotCompleted() = runTest {
        progressRepository.markRequirementCompleted("camping", "1", day, badgeStart)
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals(BadgeStatus.InProgress, viewModel.ready().status)
        assertFalse(viewModel.ready().completed)
    }

    @Test
    fun badgeMarkedCompletedOnPriorDate_isCompleted() = runTest {
        progressRepository.setCompletedOnPriorDate("camping", day, badgeStart)
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertTrue(viewModel.ready().completed)
        assertEquals(day, viewModel.ready().completedOn)
    }

    @Test
    fun badgeCompleteFromItsRequirements_isCompletedOnTheDateTheyWere() = runTest {
        val viewModel = viewModel("chess")
        startCollecting(viewModel)

        completeChess()

        assertEquals(day, viewModel.ready().completedOn)
    }

    @Test
    fun badgeNotComplete_hasNoCompletionDate() = runTest {
        progressRepository.markRequirementCompleted("camping", "1", day, badgeStart)
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertNull(viewModel.ready().completedOn)
    }

    @Test
    fun markCompleted_onABadgeNotStarted_startsItAndCompletesIt() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)
        assertFalse(viewModel.ready().canClear)

        viewModel.markCompleted(day)

        // Started as when anything is recorded: on the newest requirements, today.
        assertEquals(
            BadgeProgress("camping", newest, today, completedOnPriorDate = day),
            progressRepository.observeProgress("camping").first()!!.badge
        )
        assertTrue(viewModel.ready().completed)
        assertEquals(day, viewModel.ready().completedOnPriorDate)
        assertNull(viewModel.ready().fractionDone)
        assertTrue(viewModel.ready().canClear)
    }

    @Test
    fun markCompleted_keepsWhatsRecorded_andTheRestReadsAsNotRecorded() = runTest {
        progressRepository.markRequirementCompleted("camping", "1", day, badgeStart)
        val viewModel = viewModel()
        startCollecting(viewModel)
        assertTrue(viewModel.ready().requirements.none { it.notRecorded })

        viewModel.markCompleted(day)

        val notRecorded = viewModel.ready().requirements.filter { it.notRecorded }
        assertEquals(true, viewModel.completed()["1"])
        assertEquals(listOf("2", "3", "4"), notRecorded.map { it.number })
    }

    @Test
    fun markCompleted_onAMarkedBadge_changesTheDate() = runTest {
        progressRepository.setCompletedOnPriorDate("camping", day, badgeStart)
        val viewModel = viewModel()
        startCollecting(viewModel)

        viewModel.markCompleted(older)

        assertEquals(older, viewModel.ready().completedOnPriorDate)
    }

    @Test
    fun unmarkCompleted_keepsTheBadgeStarted_withWhatsRecorded() = runTest {
        progressRepository.markRequirementCompleted("camping", "1", day, badgeStart)
        progressRepository.setCompletedOnPriorDate("camping", day, badgeStart)
        val viewModel = viewModel()
        startCollecting(viewModel)

        viewModel.unmarkCompleted()

        assertNull(viewModel.ready().completedOnPriorDate)
        assertFalse(viewModel.ready().completed)
        assertTrue(viewModel.ready().canClear)
        assertEquals(true, viewModel.completed()["1"])
        assertTrue(viewModel.ready().requirements.none { it.notRecorded })
    }

    @Test
    fun unmarkCompleted_remembersTheDate_untilTheBadgeIsMarkedAgain() = runTest {
        progressRepository.setCompletedOnPriorDate("camping", day, badgeStart)
        val viewModel = viewModel()
        startCollecting(viewModel)
        assertNull(viewModel.ready().unmarkedDate)

        viewModel.unmarkCompleted()
        assertEquals(day, viewModel.ready().unmarkedDate)

        viewModel.markCompleted(older)
        assertNull(viewModel.ready().unmarkedDate)
    }

    @Test
    fun unmarkCompleted_afterTheSystemStopsTheApp_remembersTheDate() = runTest {
        progressRepository.setCompletedOnPriorDate("camping", day, badgeStart)
        // Saves the ViewModel's state and restores it into a new one, as when the system stops
        // the app.
        viewModelScenario {
            BadgeDetailViewModel(
                "camping",
                catalogRepository,
                progressRepository,
                reportRepository,
                clock,
                createSavedStateHandle()
            )
        }.use { scenario ->
            startCollecting(scenario.viewModel)
            scenario.viewModel.unmarkCompleted()

            scenario.recreate()
            val restored = scenario.viewModel
            startCollecting(restored)

            assertEquals(day, restored.ready().unmarkedDate)
        }
    }

    @Test
    fun anotherValueUnderTheUnmarkedDateKey_isIgnored() = runTest {
        // A value the ViewModel didn't keep.
        val viewModel =
            viewModel(
                savedStateHandle = SavedStateHandle(mapOf("unmarkedDate" to "From an intent."))
            )
        startCollecting(viewModel)

        assertNull(viewModel.ready().unmarkedDate)
    }

    @Test
    fun clear_forgetsTheUnmarkedDate() = runTest {
        progressRepository.setCompletedOnPriorDate("camping", day, badgeStart)
        val viewModel = viewModel()
        startCollecting(viewModel)
        viewModel.unmarkCompleted()

        viewModel.clear()

        assertNull(viewModel.ready().unmarkedDate)
    }

    @Test
    fun badgeCompleteFromItsRequirements_isntMarkedCompletedOnPriorDate() = runTest {
        val viewModel = viewModel("chess")
        startCollecting(viewModel)

        completeChess()

        assertTrue(viewModel.ready().completed)
        assertNull(viewModel.ready().completedOnPriorDate)
    }

    @Test
    fun markCompleted_whenItCantBeSaved_showsFailure_andMarksNothing() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)
        progressRepository.failSaves = true

        viewModel.markCompleted(day)

        assertNotNull(viewModel.ready().saveFailure)
        assertFalse(viewModel.ready().completed)
        assertFalse(viewModel.ready().canClear)
    }

    @Test
    fun unmarkCompleted_whenItCantBeSaved_showsFailure_andKeepsTheMark() = runTest {
        progressRepository.setCompletedOnPriorDate("camping", day, badgeStart)
        val viewModel = viewModel()
        startCollecting(viewModel)
        progressRepository.failSaves = true

        viewModel.unmarkCompleted()

        assertNotNull(viewModel.ready().saveFailure)
        assertEquals(day, viewModel.ready().completedOnPriorDate)
    }

    @Test
    fun today_readsTheClockEachTime() = runTest {
        val viewModel = viewModel()
        assertEquals(today, viewModel.today())

        clock.now = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)

        assertEquals(today.plusDays(1), viewModel.today())
    }

    @Test
    fun fractionDone_badgeNotStarted_isNull() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertNull(viewModel.ready().fractionDone)
    }

    @Test
    fun fractionDone_badgeInProgress_givesPartialCredit_andUpdates() = runTest {
        progressRepository.startBadge("camping", newest, started)
        val viewModel = viewModel()
        startCollecting(viewModel)
        assertEquals(0f, viewModel.ready().fractionDone)

        // Half of requirement 2's two choices, out of four top-level requirements.
        progressRepository.markRequirementCompleted("camping", "2a", day, badgeStart)
        assertEquals(0.125f, viewModel.ready().fractionDone)

        progressRepository.markRequirementCompleted("camping", "1", day, badgeStart)
        assertEquals(0.375f, viewModel.ready().fractionDone)
    }

    @Test
    fun fractionDone_badgeComplete_isNull() = runTest {
        val viewModel = viewModel("chess")
        startCollecting(viewModel)

        completeChess()

        assertNull(viewModel.ready().fractionDone)
    }

    @Test
    fun fractionDone_badgeMarkedCompletedOnPriorDate_isNull() = runTest {
        progressRepository.setCompletedOnPriorDate("camping", day, badgeStart)
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertNull(viewModel.ready().fractionDone)
    }

    @Test
    fun shareReport_createsTheReport_forTheScreenToShare() = runTest {
        completeChess()
        val viewModel = viewModel("chess")
        startCollecting(viewModel)
        assertNull(viewModel.ready().reportToShare)

        viewModel.shareReport()

        assertEquals(listOf("chess"), reportRepository.shared)
        assertEquals(FakeReportRepository.reportUri("chess"), viewModel.ready().reportToShare)
    }

    @Test
    fun onReportShared_clearsTheReportToShare() = runTest {
        completeChess()
        val viewModel = viewModel("chess")
        startCollecting(viewModel)
        viewModel.shareReport()

        viewModel.onReportShared()

        assertNull(viewModel.ready().reportToShare)
    }

    // A double tap on Share report would otherwise open the share sheet twice.
    @Test
    fun shareReport_whileTheReportIsBeingCreated_createsItOnce() = runTest {
        completeChess()
        val viewModel = viewModel("chess")
        startCollecting(viewModel)
        val writing = CompletableDeferred<Unit>()
        reportRepository.writing = writing

        viewModel.shareReport()
        viewModel.shareReport()
        assertNull(viewModel.ready().reportToShare)
        writing.complete(Unit)

        assertEquals(listOf("chess"), reportRepository.shared)
        assertEquals(FakeReportRepository.reportUri("chess"), viewModel.ready().reportToShare)

        // Once it's ready, it can be shared again.
        viewModel.onReportShared()
        viewModel.shareReport()
        assertEquals(listOf("chess", "chess"), reportRepository.shared)
    }

    @Test
    fun shareReport_whenTheReportCantBeCreated_showsFailureUntilItsShown() = runTest {
        completeChess()
        val viewModel = viewModel("chess")
        startCollecting(viewModel)
        reportRepository.failSaves = true

        viewModel.shareReport()

        val failure = viewModel.ready().reportFailure
        assertNotNull(failure)
        assertNull(viewModel.ready().reportToShare)

        viewModel.onReportFailureShown(failure!!)
        assertNull(viewModel.ready().reportFailure)
    }

    @Test
    fun saveReport_savesItWhereTheScoutChose() = runTest {
        completeChess()
        val viewModel = viewModel("chess")
        startCollecting(viewModel)
        val destination = Uri.parse("content://documents/chess-report.pdf")

        viewModel.saveReport(destination)

        assertEquals(listOf("chess" to destination), reportRepository.saved)
        assertNull(viewModel.ready().reportFailure)
    }

    @Test
    fun saveReport_whenItCantBeSaved_showsFailure() = runTest {
        completeChess()
        val viewModel = viewModel("chess")
        startCollecting(viewModel)
        reportRepository.failSaves = true

        viewModel.saveReport(Uri.parse("content://documents/chess-report.pdf"))

        assertNotNull(viewModel.ready().reportFailure)
    }

    @Test
    fun badgeNotStarted_cantBeCleared() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertFalse(viewModel.ready().canClear)
    }

    // A badge stays started, and in progress, after everything recorded for it is undone.
    @Test
    fun badgeStarted_withNothingRecorded_canBeCleared() = runTest {
        progressRepository.startBadge("camping", newest, started)
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertTrue(viewModel.ready().canClear)
    }

    // The lowest rank here, needing one Eagle-required badge, such as Camping.
    private val star = Rank(
        id = "star",
        name = "Star",
        summary = "Our summary of Star.",
        officialUrl = "https://www.scouting.org/star/",
        requirementVersions = listOf(
            RequirementsVersion(
                newest,
                listOf(Requirement("1", "Earn a badge.", meritBadges = MeritBadgesNeeded(1, 1)))
            )
        )
    )

    @Test
    fun completedBadge_clearUnearnsTheRanksItCompletes() = runTest {
        catalogRepository.ranks = listOf(star)
        progressRepository.startBadge("star", newest, started)
        progressRepository.setCompletedOnPriorDate("camping", day, badgeStart)
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals(listOf("Star"), viewModel.ready().unearnedByClear)
    }

    @Test
    fun badgeNotComplete_clearUnearnsNothing() = runTest {
        catalogRepository.ranks = listOf(star)
        progressRepository.startBadge("star", newest, started)
        progressRepository.startBadge("camping", newest, started)
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals(emptyList<String>(), viewModel.ready().unearnedByClear)
    }

    @Test
    fun clear_removesEverythingRecordedForTheBadge_andNothingElse() = runTest {
        progressRepository.markRequirementCompleted("camping", "1", day, badgeStart)
        progressRepository.setRequirementSignOffAndComment(
            "camping",
            "2a",
            null,
            "Made chili.",
            badgeStart
        )
        progressRepository.addTrackerEntry(
            "camping",
            "3",
            null,
            mapOf("night" to "2026-04-10"),
            started,
            badgeStart
        )
        progressRepository.setCounselor("camping", Counselor("Pat Lee", null, null), badgeStart)
        progressRepository.setCompletedOnPriorDate("camping", day, badgeStart)
        completeChess()
        val chessBefore = progressRepository.observeProgress("chess").first()
        val viewModel = viewModel()
        startCollecting(viewModel)
        assertTrue(viewModel.ready().completed)

        viewModel.clear()

        assertNull(progressRepository.observeProgress("camping").first())
        assertEquals(chessBefore, progressRepository.observeProgress("chess").first())
        val ready = viewModel.ready()
        assertFalse(ready.canClear)
        assertFalse(ready.completed)
        assertNull(ready.counselor)
        assertTrue(ready.requirements.none { it.completed })
        assertEquals(TrackerCount(0, null, "nights"), ready.requirements[2].tracker)
        assertNull(ready.saveFailure)
    }

    // Otherwise the share sheet would open with a report of what the scout just cleared.
    @Test
    fun clear_whileAReportIsBeingCreatedToShare_dropsIt() = runTest {
        completeChess()
        val viewModel = viewModel("chess")
        startCollecting(viewModel)
        val writing = CompletableDeferred<Unit>()
        reportRepository.writing = writing
        viewModel.shareReport()

        viewModel.clear()
        writing.complete(Unit)

        assertNull(viewModel.ready().reportToShare)
        assertNull(viewModel.ready().reportFailure)
        assertNull(progressRepository.observeProgress("chess").first())
    }

    private val clearSaved = CompletableDeferred<Unit>()

    /** Saves a clear only once [clearSaved] lets it through, as Room takes a moment to. */
    private val slowClears = object : ProgressRepository by progressRepository {
        override suspend fun clearBadge(badgeId: String) {
            clearSaved.await()
            progressRepository.clearBadge(badgeId)
        }
    }

    private val markSaved = CompletableDeferred<Unit>()

    /** Saves a prior date only once [markSaved] lets it through, as Room takes a moment to. */
    private val slowMarks = object : ProgressRepository by progressRepository {
        override suspend fun setCompletedOnPriorDate(
            badgeId: String,
            date: LocalDate,
            start: BadgeStart
        ) {
            markSaved.await()
            progressRepository.setCompletedOnPriorDate(badgeId, date, start)
        }
    }

    // As when the scout changes the date, then taps Share report straight away, so the report
    // has the new date.
    @Test
    fun shareReport_rightAfterChangingTheDate_waitsForIt() = runTest {
        progressRepository.setCompletedOnPriorDate("camping", day, badgeStart)
        val viewModel = viewModel(progress = slowMarks)
        startCollecting(viewModel)

        viewModel.markCompleted(older)
        viewModel.shareReport()
        assertEquals(emptyList<String>(), reportRepository.shared)
        markSaved.complete(Unit)

        assertEquals(listOf("camping"), reportRepository.shared)
        assertEquals(older, viewModel.ready().completedOnPriorDate)
    }

    // As when the scout confirms Clear, then taps Share report before the page redraws.
    @Test
    fun shareReport_rightAfterAClear_waitsForIt_andSharesNothing() = runTest {
        completeChess()
        val viewModel = viewModel("chess", slowClears)
        startCollecting(viewModel)

        viewModel.clear()
        viewModel.shareReport()
        assertEquals(emptyList<String>(), reportRepository.shared)
        clearSaved.complete(Unit)

        assertEquals(listOf("chess"), reportRepository.shared)
        assertNull(viewModel.ready().reportToShare)
        assertNull(viewModel.ready().reportFailure)
    }

    @Test
    fun saveReport_rightAfterAClear_waitsForIt_andSavesNothing() = runTest {
        completeChess()
        val viewModel = viewModel("chess", slowClears)
        startCollecting(viewModel)

        viewModel.clear()
        viewModel.saveReport(Uri.parse("content://documents/chess-report.pdf"))
        clearSaved.complete(Unit)

        assertEquals(emptyList<Pair<String, Uri>>(), reportRepository.saved)
        assertNull(viewModel.ready().reportFailure)
    }

    @Test
    fun shareReport_afterAClearThatFailed_sharesTheReport() = runTest {
        completeChess()
        val viewModel = viewModel("chess")
        startCollecting(viewModel)
        progressRepository.failSaves = true
        viewModel.clear()
        progressRepository.failSaves = false

        viewModel.shareReport()

        assertEquals(FakeReportRepository.reportUri("chess"), viewModel.ready().reportToShare)
    }

    @Test
    fun clear_whenItCantBeSaved_showsFailureUntilItsShown() = runTest {
        progressRepository.markRequirementCompleted("camping", "1", day, badgeStart)
        val viewModel = viewModel()
        startCollecting(viewModel)
        progressRepository.failSaves = true

        viewModel.clear()

        val failure = viewModel.ready().saveFailure
        assertNotNull(failure)
        // Told as a save that failed, not as a report.
        assertNull(viewModel.ready().reportFailure)
        assertTrue(viewModel.ready().canClear)
        assertEquals(true, viewModel.completed()["1"])

        viewModel.onSaveFailureShown(failure!!)
        assertNull(viewModel.ready().saveFailure)
    }
}
