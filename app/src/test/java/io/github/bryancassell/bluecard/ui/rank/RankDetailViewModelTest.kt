package io.github.bryancassell.bluecard.ui.rank

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
import io.github.bryancassell.bluecard.data.progress.BadgeProgress
import io.github.bryancassell.bluecard.data.progress.BadgeStart
import io.github.bryancassell.bluecard.data.progress.FakeProgressRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.RankStatus
import io.github.bryancassell.bluecard.data.report.FakeReportRepository
import io.github.bryancassell.bluecard.testing.FakeClock
import io.github.bryancassell.bluecard.testing.MainDispatcherRule
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
class RankDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val older = LocalDate.of(2025, 1, 1)
    private val newest = LocalDate.of(2026, 1, 1)
    private val started = LocalDate.of(2026, 3, 1)
    private val day = LocalDate.of(2026, 4, 15)
    private val rankStart = BadgeStart(newest, started)
    private val today = LocalDate.of(2026, 5, 20)
    private val clock = FakeClock(today.atTime(12, 0).toInstant(ZoneOffset.UTC))

    // Each rank's newest version needs requirements 1 and 2.
    private fun rank(id: String, name: String) = Rank(
        id = id,
        name = name,
        summary = "Our summary of $name.",
        officialUrl = "https://www.scouting.org/$id.pdf",
        requirementVersions = listOf(
            RequirementsVersion(older, listOf(Requirement("1", "An older first requirement."))),
            RequirementsVersion(
                newest,
                listOf(Requirement("1", "First."), Requirement("2", "Second."))
            )
        )
    )

    private val catalogRepository = FakeCatalogRepository(
        ranks = listOf(
            rank("scout", "Scout"),
            rank("tenderfoot", "Tenderfoot"),
            rank("second-class", "Second Class")
        )
    )
    private val progressRepository = FakeProgressRepository()
    private val reportRepository = FakeReportRepository(progressRepository, catalogRepository)

    // Created in the test, after MainDispatcherRule has replaced the Main dispatcher that
    // viewModelScope uses.
    private fun viewModel(
        rankId: String = "tenderfoot",
        progress: ProgressRepository = progressRepository,
        savedStateHandle: SavedStateHandle = SavedStateHandle()
    ) = RankDetailViewModel(
        rankId,
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
    private fun TestScope.startCollecting(viewModel: RankDetailViewModel) {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }
    }

    private fun RankDetailViewModel.ready() = uiState.value as RankDetailUiState.Ready

    private suspend fun complete(rankId: String, vararg numbers: String) {
        numbers.forEach { progressRepository.markRequirementCompleted(rankId, it, day, rankStart) }
    }

    @Test
    fun uiState_whileCatalogLoads_isLoading() = runTest {
        val loading = object : CatalogRepository {
            override suspend fun getBadges(): List<MeritBadge> = awaitCancellation()
            override suspend fun getRanks(): List<Rank> = awaitCancellation()
        }
        val viewModel = RankDetailViewModel(
            "scout",
            loading,
            progressRepository,
            reportRepository,
            clock,
            SavedStateHandle()
        )
        startCollecting(viewModel)

        assertEquals(RankDetailUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun uiState_whenCatalogCantBeRead_isLoadFailed() = runTest {
        catalogRepository.failLoads = true
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals(RankDetailUiState.LoadFailed, viewModel.uiState.value)
    }

    @Test
    fun uiState_whenProgressCantBeRead_isLoadFailed() = runTest {
        progressRepository.failLoads = true
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals(RankDetailUiState.LoadFailed, viewModel.uiState.value)
    }

    @Test
    fun notStarted_showsRankWithNewestRequirements() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)

        val ready = viewModel.ready()
        assertEquals("Tenderfoot", ready.name)
        assertEquals("Our summary of Tenderfoot.", ready.summary)
        assertEquals("https://www.scouting.org/tenderfoot.pdf", ready.officialUrl)
        assertEquals(listOf("1", "2"), ready.requirements.map { it.number })
        assertEquals(listOf("First.", "Second."), ready.requirements.map { it.summary })
        assertEquals(RankStatus.NotEarned, ready.status)
        assertNull(ready.fractionDone)
        assertNull(ready.earnedOnPriorDate)
        assertNull(ready.earnedWith)
        assertFalse(ready.canClear)
    }

    @Test
    fun startedOnOlderVersion_showsThatVersionsRequirements() = runTest {
        progressRepository.startBadge("tenderfoot", older, started)
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals(
            listOf("An older first requirement."),
            viewModel.ready().requirements.map { it.summary }
        )
    }

    @Test
    fun rankMissingFromCatalog_isUnavailable() = runTest {
        val viewModel = viewModel("star")
        startCollecting(viewModel)

        assertEquals(RankDetailUiState.Unavailable, viewModel.uiState.value)
    }

    @Test
    fun startedOnVersionMissingFromCatalog_isUnavailable() = runTest {
        progressRepository.startBadge("tenderfoot", LocalDate.of(2024, 1, 1), started)
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals(RankDetailUiState.Unavailable, viewModel.uiState.value)
    }

    // Badges and ranks are told apart by ID, as the catalog keeps them unique across both.
    @Test
    fun badgeId_isUnavailable() = runTest {
        catalogRepository.badges = listOf(
            MeritBadge(
                id = "camping",
                name = "Camping",
                summary = "Our summary of Camping.",
                officialUrl = "https://www.scouting.org/merit-badges/camping/",
                requirementVersions = listOf(
                    RequirementsVersion(newest, listOf(Requirement("1", "First.")))
                )
            )
        )
        val viewModel = viewModel("camping")
        startCollecting(viewModel)

        assertEquals(RankDetailUiState.Unavailable, viewModel.uiState.value)
    }

    @Test
    fun lowestRankNotEarned_isInProgress_evenBeforeItsStarted() = runTest {
        val viewModel = viewModel("scout")
        startCollecting(viewModel)

        assertEquals(RankStatus.InProgress, viewModel.ready().status)
        assertEquals(0f, viewModel.ready().fractionDone)
    }

    @Test
    fun status_dependsOnTheRanksBelow_andUpdatesWhenTheirProgressChanges() = runTest {
        complete("tenderfoot", "1", "2")
        val viewModel = viewModel()
        startCollecting(viewModel)
        // All done, but waiting on Scout.
        assertEquals(RankStatus.NotEarned, viewModel.ready().status)
        assertEquals(1f, viewModel.ready().fractionDone)

        complete("scout", "1", "2")

        assertEquals(RankStatus.Earned, viewModel.ready().status)
        assertNull(viewModel.ready().fractionDone)
    }

    @Test
    fun requirements_showWhetherEachIsComplete_andUpdate() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)

        complete("tenderfoot", "2")

        assertEquals(
            listOf(false, true),
            viewModel.ready().requirements.map { it.completed }
        )
        assertEquals(0.5f, viewModel.ready().fractionDone)
        assertTrue(viewModel.ready().canClear)
    }

    // Scout's requirements are done, and Tenderfoot's 2 asks for one Eagle-required badge.
    @Test
    fun requirementThatAsksForMeritBadges_completesFromBadgeProgress_andEarnsTheRank() = runTest {
        val earn = Requirement("2", "Earn a badge.", meritBadges = MeritBadgesNeeded(1, 1))
        catalogRepository.badges = listOf(
            MeritBadge(
                id = "camping",
                name = "Camping",
                summary = "Our summary of Camping.",
                officialUrl = "https://www.scouting.org/merit-badges/camping/",
                eagleRequired = true,
                requirementVersions = emptyList()
            )
        )
        catalogRepository.ranks = listOf(
            rank("scout", "Scout"),
            rank("tenderfoot", "Tenderfoot").copy(
                requirementVersions = listOf(
                    RequirementsVersion(newest, listOf(Requirement("1", "First."), earn))
                )
            )
        )
        complete("scout", "1", "2")
        complete("tenderfoot", "1")
        val viewModel = viewModel()
        startCollecting(viewModel)
        assertEquals(listOf(true, false), viewModel.ready().requirements.map { it.completed })

        progressRepository.setCompletedOnPriorDate("camping", today, rankStart)

        assertEquals(listOf(true, true), viewModel.ready().requirements.map { it.completed })
        assertEquals(RankStatus.Earned, viewModel.ready().status)
        assertEquals(today, viewModel.ready().earnedOn)
    }

    @Test
    fun markEarned_onARankNotStarted_startsItAndEarnsIt() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)

        viewModel.markEarned(day)

        // Started as when anything is recorded: on the newest requirements, today.
        assertEquals(
            BadgeProgress("tenderfoot", newest, today, completedOnPriorDate = day),
            progressRepository.observeProgress("tenderfoot").first()!!.badge
        )
        assertEquals(RankStatus.Earned, viewModel.ready().status)
        assertEquals(day, viewModel.ready().earnedOnPriorDate)
        assertNull(viewModel.ready().earnedWith)
        assertTrue(viewModel.ready().canClear)
        // What isn't recorded for it reads "Not recorded".
        assertTrue(viewModel.ready().requirements.all { it.notRecorded })
    }

    @Test
    fun markEarned_countsTheRanksBelowAsEarned_withIt() = runTest {
        val scout = viewModel("scout")
        startCollecting(scout)
        val tenderfoot = viewModel()
        startCollecting(tenderfoot)

        tenderfoot.markEarned(day)

        assertEquals(RankStatus.Earned, scout.ready().status)
        assertEquals("Tenderfoot", scout.ready().earnedWith)
        assertNull(scout.ready().earnedOnPriorDate)
        assertNull(scout.ready().fractionDone)
        assertTrue(scout.ready().requirements.all { it.notRecorded })
        // Nothing is stored for Scout.
        assertNull(progressRepository.observeProgress("scout").first())
        assertFalse(scout.ready().canClear)
    }

    @Test
    fun markEarned_onARankEarnedWithARankAbove_givesItADateOfItsOwn() = runTest {
        progressRepository.setCompletedOnPriorDate("tenderfoot", day, rankStart)
        val viewModel = viewModel("scout")
        startCollecting(viewModel)

        viewModel.markEarned(older)

        assertEquals(older, viewModel.ready().earnedOnPriorDate)
        assertNull(viewModel.ready().earnedWith)
    }

    @Test
    fun markEarned_onAMarkedRank_changesTheDate() = runTest {
        progressRepository.setCompletedOnPriorDate("tenderfoot", day, rankStart)
        val viewModel = viewModel()
        startCollecting(viewModel)

        viewModel.markEarned(older)

        assertEquals(older, viewModel.ready().earnedOnPriorDate)
    }

    @Test
    fun unmarkEarned_keepsTheRankStarted_withWhatsRecorded_andUndoesTheRanksBelow() = runTest {
        complete("tenderfoot", "1")
        progressRepository.setCompletedOnPriorDate("tenderfoot", day, rankStart)
        val scout = viewModel("scout")
        startCollecting(scout)
        val viewModel = viewModel()
        startCollecting(viewModel)

        viewModel.unmarkEarned()

        assertNull(viewModel.ready().earnedOnPriorDate)
        assertEquals(RankStatus.NotEarned, viewModel.ready().status)
        assertTrue(viewModel.ready().canClear)
        assertEquals(listOf(true, false), viewModel.ready().requirements.map { it.completed })
        assertTrue(viewModel.ready().requirements.none { it.notRecorded })
        assertEquals(RankStatus.InProgress, scout.ready().status)
        assertNull(scout.ready().earnedWith)
    }

    @Test
    fun unmarkEarned_remembersTheDate_untilTheRankIsMarkedAgain() = runTest {
        progressRepository.setCompletedOnPriorDate("tenderfoot", day, rankStart)
        val viewModel = viewModel()
        startCollecting(viewModel)
        assertNull(viewModel.ready().unmarkedDate)

        viewModel.unmarkEarned()
        assertEquals(day, viewModel.ready().unmarkedDate)

        viewModel.markEarned(older)
        assertNull(viewModel.ready().unmarkedDate)
    }

    @Test
    fun unmarkEarned_afterTheSystemStopsTheApp_remembersTheDate() = runTest {
        progressRepository.setCompletedOnPriorDate("tenderfoot", day, rankStart)
        // Saves the ViewModel's state and restores it into a new one, as when the system stops
        // the app.
        viewModelScenario {
            RankDetailViewModel(
                "tenderfoot",
                catalogRepository,
                progressRepository,
                reportRepository,
                clock,
                createSavedStateHandle()
            )
        }.use { scenario ->
            startCollecting(scenario.viewModel)
            scenario.viewModel.unmarkEarned()

            scenario.recreate()
            val restored = scenario.viewModel
            startCollecting(restored)

            assertEquals(day, restored.ready().unmarkedDate)
        }
    }

    @Test
    fun anotherValueUnderTheUnmarkedDateKey_isIgnored() = runTest {
        // A value the ViewModel didn't keep.
        val viewModel = viewModel(
            savedStateHandle = SavedStateHandle(mapOf("unmarkedDate" to "From an intent."))
        )
        startCollecting(viewModel)

        assertNull(viewModel.ready().unmarkedDate)
    }

    @Test
    fun clear_forgetsTheUnmarkedDate() = runTest {
        progressRepository.setCompletedOnPriorDate("tenderfoot", day, rankStart)
        val viewModel = viewModel()
        startCollecting(viewModel)
        viewModel.unmarkEarned()

        viewModel.clear()

        assertNull(viewModel.ready().unmarkedDate)
    }

    @Test
    fun rankCompleteFromItsRequirements_isntMarkedEarned_andIsEarnedOnTheirDate() = runTest {
        val viewModel = viewModel("scout")
        startCollecting(viewModel)

        complete("scout", "1", "2")

        assertEquals(RankStatus.Earned, viewModel.ready().status)
        assertNull(viewModel.ready().earnedOnPriorDate)
        assertNull(viewModel.ready().earnedWith)
        assertEquals(day, viewModel.ready().earnedOn)
    }

    @Test
    fun rankCompleteFromItsRequirements_waitsOnTheRankBelow_untilItsEarned() = runTest {
        complete("tenderfoot", "1", "2")
        val viewModel = viewModel()
        startCollecting(viewModel)
        assertEquals("Scout", viewModel.ready().waitingOn)
        assertNull(viewModel.ready().earnedOn)

        complete("scout", "1", "2")

        assertNull(viewModel.ready().waitingOn)
        assertEquals(day, viewModel.ready().earnedOn)
    }

    @Test
    fun rankNotComplete_waitsOnNothing() = runTest {
        complete("tenderfoot", "1")
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertNull(viewModel.ready().waitingOn)
    }

    @Test
    fun markEarned_whenItCantBeSaved_showsFailure_andMarksNothing() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)
        progressRepository.failSaves = true

        viewModel.markEarned(day)

        assertNotNull(viewModel.ready().saveFailure)
        assertEquals(RankStatus.NotEarned, viewModel.ready().status)
        assertFalse(viewModel.ready().canClear)
    }

    @Test
    fun unmarkEarned_whenItCantBeSaved_showsFailure_andKeepsTheMark() = runTest {
        progressRepository.setCompletedOnPriorDate("tenderfoot", day, rankStart)
        val viewModel = viewModel()
        startCollecting(viewModel)
        progressRepository.failSaves = true

        viewModel.unmarkEarned()

        assertNotNull(viewModel.ready().saveFailure)
        assertEquals(day, viewModel.ready().earnedOnPriorDate)
    }

    @Test
    fun clear_removesEverythingRecordedForTheRank_andNothingElse() = runTest {
        complete("scout", "1", "2")
        complete("tenderfoot", "1")
        progressRepository.setRequirementComment("tenderfoot", "2", "Hiked.", rankStart)
        progressRepository.setCompletedOnPriorDate("tenderfoot", day, rankStart)
        val scoutBefore = progressRepository.observeProgress("scout").first()
        val viewModel = viewModel()
        startCollecting(viewModel)

        viewModel.clear()

        assertNull(progressRepository.observeProgress("tenderfoot").first())
        assertEquals(scoutBefore, progressRepository.observeProgress("scout").first())
        assertFalse(viewModel.ready().canClear)
        assertEquals(RankStatus.InProgress, viewModel.ready().status)
    }

    @Test
    fun rankNotStarted_clearUnearnsNothing() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals(emptyList<String>(), viewModel.ready().unearnedByClear)
    }

    @Test
    fun markedRank_clearUnearnsTheRanksItsMarkCounts() = runTest {
        progressRepository.setCompletedOnPriorDate("second-class", day, rankStart)
        val viewModel = viewModel("second-class")
        startCollecting(viewModel)

        assertEquals(listOf("Scout", "Tenderfoot"), viewModel.ready().unearnedByClear)
    }

    @Test
    fun rankEarnedInOrder_clearUnearnsTheRanksEarnedAfterIt() = runTest {
        complete("scout", "1", "2")
        complete("tenderfoot", "1", "2")
        val viewModel = viewModel("scout")
        startCollecting(viewModel)

        assertEquals(listOf("Tenderfoot"), viewModel.ready().unearnedByClear)
    }

    // A rank above it marked earned counts them as earned either way.
    @Test
    fun markedRank_underAnotherMarkedRank_clearUnearnsNothing() = runTest {
        progressRepository.setCompletedOnPriorDate("tenderfoot", day, rankStart)
        progressRepository.setCompletedOnPriorDate("second-class", day, rankStart)
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals(emptyList<String>(), viewModel.ready().unearnedByClear)
    }

    @Test
    fun clearingRankStartedOnOlderVersion_showsNewestRequirements() = runTest {
        progressRepository.startBadge("tenderfoot", older, started)
        val viewModel = viewModel()
        startCollecting(viewModel)

        viewModel.clear()

        assertEquals(listOf("First.", "Second."), viewModel.ready().requirements.map { it.summary })
    }

    @Test
    fun clear_whenItCantBeSaved_showsFailureUntilItsShown() = runTest {
        complete("tenderfoot", "1")
        val viewModel = viewModel()
        startCollecting(viewModel)
        progressRepository.failSaves = true

        viewModel.clear()

        val failure = viewModel.ready().saveFailure
        assertNotNull(failure)
        assertTrue(viewModel.ready().canClear)

        viewModel.onSaveFailureShown(failure!!)
        assertNull(viewModel.ready().saveFailure)
    }

    @Test
    fun shareReport_ofAnEarnedRank_createsItsReport_forTheScreenToShare() = runTest {
        complete("scout", "1", "2")
        val viewModel = viewModel("scout")
        startCollecting(viewModel)
        assertNull(viewModel.ready().reportToShare)

        viewModel.shareReport()

        assertEquals(listOf("scout"), reportRepository.shared)
        assertEquals(FakeReportRepository.reportUri("scout"), viewModel.ready().reportToShare)

        viewModel.onReportShared()

        assertNull(viewModel.ready().reportToShare)
    }

    // It has a report, though it isn't started.
    @Test
    fun shareReport_ofARankEarnedWithARankAbove_createsItsReport() = runTest {
        progressRepository.setCompletedOnPriorDate("tenderfoot", day, rankStart)
        val viewModel = viewModel("scout")
        startCollecting(viewModel)

        viewModel.shareReport()

        assertEquals(FakeReportRepository.reportUri("scout"), viewModel.ready().reportToShare)
    }

    @Test
    fun saveReport_savesItWhereTheScoutChose() = runTest {
        complete("scout", "1", "2")
        val viewModel = viewModel("scout")
        startCollecting(viewModel)
        val destination = Uri.parse("content://documents/scout-report.pdf")

        viewModel.saveReport(destination)

        assertEquals(listOf("scout" to destination), reportRepository.saved)
        assertNull(viewModel.ready().reportFailure)
    }

    @Test
    fun report_whenItCantBeCreatedOrSaved_showsFailureUntilItsShown() = runTest {
        complete("scout", "1", "2")
        val viewModel = viewModel("scout")
        startCollecting(viewModel)
        reportRepository.failSaves = true

        viewModel.shareReport()

        val failure = viewModel.ready().reportFailure
        assertNotNull(failure)
        assertNull(viewModel.ready().reportToShare)
        // Told as a report that failed, not as a save.
        assertNull(viewModel.ready().saveFailure)
        viewModel.onReportFailureShown(failure!!)
        assertNull(viewModel.ready().reportFailure)

        viewModel.saveReport(Uri.parse("content://documents/scout-report.pdf"))

        assertNotNull(viewModel.ready().reportFailure)
    }

    // Otherwise the share sheet would open with a report of what the scout just cleared.
    @Test
    fun clear_whileAReportIsBeingCreatedToShare_dropsIt() = runTest {
        progressRepository.setCompletedOnPriorDate("tenderfoot", day, rankStart)
        val viewModel = viewModel()
        startCollecting(viewModel)
        val writing = CompletableDeferred<Unit>()
        reportRepository.writing = writing
        viewModel.shareReport()

        viewModel.clear()
        writing.complete(Unit)

        assertNull(viewModel.ready().reportToShare)
        assertNull(viewModel.ready().reportFailure)
        assertNull(progressRepository.observeProgress("tenderfoot").first())
    }

    private val saved = CompletableDeferred<Unit>()

    /**
     * Saves marks, unmarks and clears only once [saved] lets them through, as Room takes a
     * moment to.
     */
    private val slowSaves = object : ProgressRepository by progressRepository {
        override suspend fun setCompletedOnPriorDate(
            badgeId: String,
            date: LocalDate,
            start: BadgeStart
        ) {
            saved.await()
            progressRepository.setCompletedOnPriorDate(badgeId, date, start)
        }

        override suspend fun removeCompletedOnPriorDate(badgeId: String) {
            saved.await()
            progressRepository.removeCompletedOnPriorDate(badgeId)
        }

        override suspend fun clearBadge(badgeId: String) {
            saved.await()
            progressRepository.clearBadge(badgeId)
        }
    }

    // As when the scout marks the rank earned, then taps Share report as soon as it shows.
    @Test
    fun shareReport_rightAfterMarkingTheRankEarned_waitsForIt() = runTest {
        val viewModel = viewModel(progress = slowSaves)
        startCollecting(viewModel)

        viewModel.markEarned(day)
        viewModel.shareReport()
        assertEquals(emptyList<String>(), reportRepository.shared)
        saved.complete(Unit)

        assertEquals(FakeReportRepository.reportUri("tenderfoot"), viewModel.ready().reportToShare)
    }

    // As when the scout unmarks the rank, then taps Share report before the page redraws.
    @Test
    fun shareReport_rightAfterUnmarking_waitsForIt_andSharesNothing() = runTest {
        progressRepository.setCompletedOnPriorDate("tenderfoot", day, rankStart)
        val viewModel = viewModel(progress = slowSaves)
        startCollecting(viewModel)

        viewModel.unmarkEarned()
        viewModel.shareReport()
        assertEquals(emptyList<String>(), reportRepository.shared)
        saved.complete(Unit)

        assertEquals(listOf("tenderfoot"), reportRepository.shared)
        assertNull(viewModel.ready().reportToShare)
    }

    // As when the scout confirms Clear, then taps Save report before the page redraws.
    @Test
    fun saveReport_rightAfterAClear_waitsForIt_andSavesNothing() = runTest {
        progressRepository.setCompletedOnPriorDate("tenderfoot", day, rankStart)
        val viewModel = viewModel(progress = slowSaves)
        startCollecting(viewModel)

        viewModel.clear()
        viewModel.saveReport(Uri.parse("content://documents/tenderfoot-report.pdf"))
        saved.complete(Unit)

        assertEquals(emptyList<Pair<String, Uri>>(), reportRepository.saved)
        assertNull(viewModel.ready().reportFailure)
    }

    @Test
    fun today_readsTheClockEachTime() = runTest {
        val viewModel = viewModel()
        assertEquals(today, viewModel.today())

        clock.now = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)

        assertEquals(today.plusDays(1), viewModel.today())
    }
}
