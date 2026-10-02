package io.github.bryancassell.bluecard.ui.ranks

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.catalog.FakeCatalogRepository
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Rank
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.progress.BadgeStart
import io.github.bryancassell.bluecard.data.progress.FakeProgressRepository
import io.github.bryancassell.bluecard.data.progress.RankStatus
import io.github.bryancassell.bluecard.testing.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Robolectric, because the load-failure tests reach android.util.Log, which throws in
// plain local tests (see ARCHITECTURE.md, ViewModel tests).
@RunWith(AndroidJUnit4::class)
class RanksViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val version = LocalDate.of(2026, 1, 1)
    private val started = LocalDate.of(2026, 3, 1)
    private val day = LocalDate.of(2026, 4, 15)
    private val rankStart = BadgeStart(version, started)

    private fun rank(id: String, name: String) = Rank(
        id = id,
        name = name,
        summary = "Our summary of $name.",
        officialUrl = "https://www.scouting.org/$id.pdf",
        requirementVersions = listOf(
            RequirementsVersion(
                version,
                listOf(Requirement("1", "First."), Requirement("2", "Second."))
            )
        )
    )

    // Not in alphabetical order, so the list's order is the catalog's.
    private val catalogRepository = FakeCatalogRepository(
        badges = listOf(
            MeritBadge(
                id = "camping",
                name = "Camping",
                summary = "Our summary of Camping.",
                officialUrl = "https://www.scouting.org/merit-badges/camping/",
                requirementVersions = listOf(
                    RequirementsVersion(version, listOf(Requirement("1", "First.")))
                )
            )
        ),
        ranks = listOf(
            rank("scout", "Scout"),
            rank("tenderfoot", "Tenderfoot"),
            rank("second-class", "Second Class"),
            rank("first-class", "First Class")
        )
    )
    private val progressRepository = FakeProgressRepository()

    // Created in the test, after MainDispatcherRule has replaced the Main dispatcher that
    // viewModelScope uses.
    private val viewModel by lazy { RanksViewModel(catalogRepository, progressRepository) }

    /**
     * Collects uiState, as the screen does, so WhileSubscribed starts it. From the
     * coroutines testing guide: https://developer.android.com/kotlin/coroutines/test#statein
     */
    private fun TestScope.startCollecting(viewModel: RanksViewModel) {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }
    }

    private fun ranks() = (viewModel.uiState.value as RanksUiState.Ready).ranks

    private suspend fun complete(rankId: String, vararg numbers: String) {
        numbers.forEach { progressRepository.markRequirementCompleted(rankId, it, day, rankStart) }
    }

    @Test
    fun uiState_whileCatalogLoads_isLoading() = runTest {
        val loading = object : CatalogRepository {
            override suspend fun getBadges(): List<MeritBadge> = awaitCancellation()
            override suspend fun getRanks(): List<Rank> = awaitCancellation()
        }
        val viewModel = RanksViewModel(loading, progressRepository)
        startCollecting(viewModel)

        assertEquals(RanksUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun uiState_whenCatalogCantBeRead_isLoadFailed() = runTest {
        catalogRepository.failLoads = true
        startCollecting(viewModel)

        assertEquals(RanksUiState.LoadFailed, viewModel.uiState.value)
    }

    @Test
    fun uiState_whenProgressCantBeRead_isLoadFailed() = runTest {
        progressRepository.failLoads = true
        startCollecting(viewModel)

        assertEquals(RanksUiState.LoadFailed, viewModel.uiState.value)
    }

    @Test
    fun newScout_listsEveryRankInOrder_withScoutInProgress() = runTest {
        startCollecting(viewModel)

        assertEquals(
            listOf(
                RankListItem("scout", "Scout", RankStatus.InProgress, fractionDone = 0f),
                RankListItem("tenderfoot", "Tenderfoot", RankStatus.NotEarned),
                RankListItem("second-class", "Second Class", RankStatus.NotEarned),
                RankListItem("first-class", "First Class", RankStatus.NotEarned)
            ),
            ranks()
        )
    }

    @Test
    fun ranks_showTheScoutsStanding_andUpdateWhenProgressChanges() = runTest {
        startCollecting(viewModel)

        complete("scout", "1", "2")
        complete("second-class", "1")

        assertEquals(
            listOf(
                RankListItem("scout", "Scout", RankStatus.Earned),
                RankListItem("tenderfoot", "Tenderfoot", RankStatus.InProgress, 0f),
                RankListItem("second-class", "Second Class", RankStatus.NotEarned, 0.5f),
                RankListItem("first-class", "First Class", RankStatus.NotEarned)
            ),
            ranks()
        )
    }

    @Test
    fun rankMarkedEarned_countsTheRanksBelowItAsEarned_untilUnmarked() = runTest {
        progressRepository.setCompletedOnPriorDate("second-class", day, rankStart)
        startCollecting(viewModel)
        assertEquals(
            listOf(
                RankStatus.Earned,
                RankStatus.Earned,
                RankStatus.Earned,
                RankStatus.InProgress
            ),
            ranks().map { it.status }
        )

        progressRepository.removeCompletedOnPriorDate("second-class")

        assertEquals(
            listOf(
                RankStatus.InProgress,
                RankStatus.NotEarned,
                RankStatus.NotEarned,
                RankStatus.NotEarned
            ),
            ranks().map { it.status }
        )
    }

    @Test
    fun badges_arentListed_evenWhenStarted() = runTest {
        progressRepository.markRequirementCompleted("camping", "1", day, rankStart)
        startCollecting(viewModel)

        assertEquals(
            listOf("scout", "tenderfoot", "second-class", "first-class"),
            ranks().map { it.id }
        )
    }
}
