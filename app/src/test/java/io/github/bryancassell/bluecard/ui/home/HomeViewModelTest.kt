package io.github.bryancassell.bluecard.ui.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.catalog.FakeCatalogRepository
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.MeritBadgesNeeded
import io.github.bryancassell.bluecard.data.catalog.Rank
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.profile.FakeProfileRepository
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.progress.BadgeStart
import io.github.bryancassell.bluecard.data.progress.BadgeStatus
import io.github.bryancassell.bluecard.data.progress.FakeProgressRepository
import io.github.bryancassell.bluecard.data.progress.RankStatus
import io.github.bryancassell.bluecard.testing.MainDispatcherRule
import io.github.bryancassell.bluecard.ui.badges.BadgeListItem
import io.github.bryancassell.bluecard.ui.badges.EagleRequirement
import io.github.bryancassell.bluecard.ui.ranks.RankListItem
import java.time.LocalDate
import kotlinx.coroutines.awaitCancellation
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

// Robolectric, because the load-failure tests reach android.util.Log, which throws in
// plain local tests (see ARCHITECTURE.md, ViewModel tests).
@RunWith(AndroidJUnit4::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val version = LocalDate.of(2026, 1, 1)
    private val started = LocalDate.of(2026, 3, 1)
    private val day = LocalDate.of(2026, 4, 15)
    private val badgeStart = BadgeStart(version, started)

    // Named differently from their ids, so tests can tell the two apart.
    private fun badge(
        id: String,
        name: String = id.replaceFirstChar(Char::uppercase),
        eagleRequired: Boolean = false,
        eagleGroup: String? = null
    ) = MeritBadge(
        id = id,
        name = name,
        summary = "Our summary of $id.",
        officialUrl = "https://www.scouting.org/merit-badges/$id/",
        eagleRequired = eagleRequired,
        eagleGroup = eagleGroup,
        requirementVersions = listOf(
            RequirementsVersion(
                version,
                listOf(Requirement("1", "First."), Requirement("2", "Second."))
            )
        )
    )

    // Two Eagle-required badges on their own, one "one of" group of three, and two
    // badges that aren't Eagle-required: 3 Eagle requirements in all.
    private val catalog = listOf(
        badge("camping", eagleRequired = true),
        badge("cooking", eagleRequired = true),
        badge("cycling", eagleRequired = true, eagleGroup = GROUP),
        badge("hiking", eagleRequired = true, eagleGroup = GROUP),
        badge("swimming", eagleRequired = true, eagleGroup = GROUP),
        badge("chess"),
        badge("pottery")
    )

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

    private val ranks = listOf(
        rank("scout", "Scout"),
        rank("tenderfoot", "Tenderfoot"),
        rank("second-class", "Second Class")
    )

    private val profileRepository = FakeProfileRepository(Profile("Alex Scout", "123"))
    private val catalogRepository = FakeCatalogRepository(catalog, ranks)
    private val progressRepository = FakeProgressRepository()

    // Created in the test, after MainDispatcherRule has replaced the Main dispatcher that
    // viewModelScope uses.
    private val viewModel by lazy {
        HomeViewModel(profileRepository, catalogRepository, progressRepository)
    }

    /**
     * Collects uiState, as the screen does, so WhileSubscribed starts it. From the
     * coroutines testing guide: https://developer.android.com/kotlin/coroutines/test#statein
     */
    private fun TestScope.startCollecting(viewModel: HomeViewModel) {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }
    }

    private fun ready() = viewModel.uiState.value as HomeUiState.Ready

    private fun idsInProgress() = ready().badgesInProgress.map { it.id }

    private suspend fun start(badgeId: String) {
        progressRepository.startBadge(badgeId, version, started)
    }

    private suspend fun complete(badgeId: String) {
        progressRepository.setCompletedOnPriorDate(badgeId, day, badgeStart)
    }

    @Test
    fun uiState_whileCatalogLoads_isLoading() = runTest {
        val loading = object : CatalogRepository {
            override suspend fun getBadges(): List<MeritBadge> = awaitCancellation()
            override suspend fun getRanks(): List<Rank> = awaitCancellation()
        }
        val viewModel = HomeViewModel(profileRepository, loading, progressRepository)
        startCollecting(viewModel)

        assertEquals(HomeUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun uiState_whenProfileCantBeRead_isLoadFailed() = runTest {
        profileRepository.failLoads = true
        startCollecting(viewModel)

        assertEquals(HomeUiState.LoadFailed, viewModel.uiState.value)
    }

    @Test
    fun uiState_whenCatalogCantBeRead_isLoadFailed() = runTest {
        catalogRepository.failLoads = true
        startCollecting(viewModel)

        assertEquals(HomeUiState.LoadFailed, viewModel.uiState.value)
    }

    @Test
    fun uiState_whenProgressCantBeRead_isLoadFailed() = runTest {
        progressRepository.failLoads = true
        startCollecting(viewModel)

        assertEquals(HomeUiState.LoadFailed, viewModel.uiState.value)
    }

    @Test
    fun uiState_withoutProfile_isLoading() = runTest {
        profileRepository.removeProfile()
        startCollecting(viewModel)

        assertEquals(HomeUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun noProgress_showsProfileAndEmptyCounts() = runTest {
        startCollecting(viewModel)

        assertEquals(
            HomeUiState.Ready(
                name = "Alex Scout",
                unitNumber = "123",
                ranks = listOf(
                    RankListItem("scout", "Scout", RankStatus.InProgress, 0f),
                    RankListItem("tenderfoot", "Tenderfoot", RankStatus.NotEarned),
                    RankListItem("second-class", "Second Class", RankStatus.NotEarned)
                ),
                badges = ProgressCounts(completed = 0, inProgress = 0),
                eagle = ProgressCounts(completed = 0, inProgress = 0),
                eagleTotal = 3,
                badgesInProgress = emptyList()
            ),
            viewModel.uiState.value
        )
        assertTrue(ready().hasNoProgress)
    }

    @Test
    fun badges_countsCompletedAndInProgress() = runTest {
        start("chess")
        start("camping")
        progressRepository.markRequirementCompleted("camping", "1", day, badgeStart)
        complete("pottery")
        // Completed through its requirements rather than a prior date.
        start("cooking")
        progressRepository.markRequirementCompleted("cooking", "1", day, badgeStart)
        progressRepository.markRequirementCompleted("cooking", "2", null, badgeStart)
        startCollecting(viewModel)

        assertEquals(ProgressCounts(completed = 2, inProgress = 2), ready().badges)
        assertFalse(ready().hasNoProgress)
    }

    @Test
    fun eagle_countsOnlyEagleRequiredBadges() = runTest {
        start("chess")
        complete("pottery")
        start("camping")
        complete("cooking")
        startCollecting(viewModel)

        assertEquals(ProgressCounts(completed = 1, inProgress = 1), ready().eagle)
    }

    @Test
    fun eagle_groupWithSeveralStarted_isInProgressOnce() = runTest {
        start("cycling")
        start("hiking")
        startCollecting(viewModel)

        assertEquals(ProgressCounts(completed = 0, inProgress = 1), ready().eagle)
        // Every started badge still counts on its own.
        assertEquals(ProgressCounts(completed = 0, inProgress = 2), ready().badges)
    }

    @Test
    fun eagle_groupWithOneCompleted_isCompletedOnce() = runTest {
        complete("swimming")
        complete("cycling")
        start("hiking")
        startCollecting(viewModel)

        assertEquals(ProgressCounts(completed = 1, inProgress = 0), ready().eagle)
        assertEquals(ProgressCounts(completed = 2, inProgress = 1), ready().badges)
    }

    @Test
    fun eagleTotal_countsBadgesOnTheirOwnAndEachGroupOnce() = runTest {
        catalogRepository.badges = catalog + listOf(
            badge("emergency-preparedness", eagleRequired = true, eagleGroup = "ep-lifesaving"),
            badge("lifesaving", eagleRequired = true, eagleGroup = "ep-lifesaving")
        )
        startCollecting(viewModel)

        assertEquals(4, ready().eagleTotal)
    }

    @Test
    fun eagleTotal_withNoEagleBadges_isZero() = runTest {
        catalogRepository.badges = listOf(badge("chess"))
        startCollecting(viewModel)

        assertEquals(0, ready().eagleTotal)
    }

    @Test
    fun progressForBadgeNotInCatalog_isIgnored() = runTest {
        start("retired-badge")
        startCollecting(viewModel)

        assertTrue(ready().hasNoProgress)
    }

    @Test
    fun uiState_updatesWhenProgressChanges() = runTest {
        startCollecting(viewModel)
        assertTrue(ready().hasNoProgress)

        start("camping")
        assertEquals(ProgressCounts(completed = 0, inProgress = 1), ready().badges)
        assertEquals(ProgressCounts(completed = 0, inProgress = 1), ready().eagle)

        progressRepository.setCompletedOnPriorDate("camping", day, badgeStart)
        assertEquals(ProgressCounts(completed = 1, inProgress = 0), ready().badges)
        assertEquals(ProgressCounts(completed = 1, inProgress = 0), ready().eagle)

        progressRepository.clearAll()
        assertTrue(ready().hasNoProgress)
    }

    @Test
    fun badgesInProgress_listsOnlyBadgesInProgress_alphabeticallyByName() = runTest {
        // In neither name nor id order, with ids that sort the other way from the names.
        catalogRepository.badges = listOf(
            badge("b", name = "Chess"),
            badge("c", name = "Archery"),
            badge("a", name = "Swimming"),
            badge("d", name = "Pottery"),
            badge("e", name = "Cooking")
        )
        start("a")
        start("b")
        start("c")
        complete("d")
        startCollecting(viewModel)

        assertEquals(
            listOf("Archery", "Chess", "Swimming"),
            ready().badgesInProgress.map { it.name }
        )
    }

    @Test
    fun badgesInProgress_showEachAsOnBadges() = runTest {
        start("camping")
        progressRepository.markRequirementCompleted("chess", "1", day, badgeStart)
        start("hiking")
        startCollecting(viewModel)

        assertEquals(
            listOf(
                BadgeListItem(
                    id = "camping",
                    name = "Camping",
                    eagle = EagleRequirement.Required,
                    status = BadgeStatus.InProgress,
                    fractionDone = 0f
                ),
                BadgeListItem(
                    id = "chess",
                    name = "Chess",
                    eagle = null,
                    status = BadgeStatus.InProgress,
                    fractionDone = 0.5f
                ),
                BadgeListItem(
                    id = "hiking",
                    name = "Hiking",
                    eagle = EagleRequirement.OneOf(listOf("Cycling", "Hiking", "Swimming")),
                    status = BadgeStatus.InProgress,
                    fractionDone = 0f
                )
            ),
            ready().badgesInProgress
        )
    }

    @Test
    fun badgesInProgress_leavesOutBadgeNotInCatalog() = runTest {
        start("retired-badge")
        start("chess")
        startCollecting(viewModel)

        assertEquals(listOf("chess"), idsInProgress())
    }

    // Ranks share the badges' progress tables, but aren't badges.
    @Test
    fun rankProgress_isntCountedOrListedAsABadge() = runTest {
        complete("scout")
        start("tenderfoot")
        startCollecting(viewModel)

        assertTrue(ready().hasNoProgress)
        assertEquals(emptyList<String>(), idsInProgress())
    }

    @Test
    fun ranks_areInOrderAsOnRanks_withTheHighestEarnedAndTheOneInProgress() = runTest {
        progressRepository.markRequirementCompleted("scout", "1", day, badgeStart)
        progressRepository.markRequirementCompleted("scout", "2", day, badgeStart)
        progressRepository.markRequirementCompleted("tenderfoot", "1", day, badgeStart)
        // Started out of order, so it isn't the one in progress.
        progressRepository.markRequirementCompleted("second-class", "1", day, badgeStart)
        startCollecting(viewModel)

        assertEquals(
            listOf(
                RankListItem("scout", "Scout", RankStatus.Earned),
                RankListItem("tenderfoot", "Tenderfoot", RankStatus.InProgress, 0.5f),
                RankListItem("second-class", "Second Class", RankStatus.NotEarned, 0.5f)
            ),
            ready().ranks
        )
        assertEquals("scout", ready().rank?.id)
        assertEquals("tenderfoot", ready().nextRank?.id)
    }

    @Test
    fun rankMarkedEarned_countsTheRanksBelowIt_untilUnmarked() = runTest {
        complete("tenderfoot")
        startCollecting(viewModel)
        assertEquals("tenderfoot", ready().rank?.id)
        assertEquals("second-class", ready().nextRank?.id)

        progressRepository.removeCompletedOnPriorDate("tenderfoot")

        assertNull(ready().rank)
        assertEquals("scout", ready().nextRank?.id)
    }

    @Test
    fun nextRank_onceEveryRankIsEarned_isNull() = runTest {
        complete("second-class")
        startCollecting(viewModel)

        assertEquals("second-class", ready().rank?.id)
        assertNull(ready().nextRank)
    }

    @Test
    fun nextRank_countsBadgesCompleted() = runTest {
        val earn = Requirement("2", "Earn a badge.", meritBadges = MeritBadgesNeeded(1, 1))
        catalogRepository.ranks = listOf(
            rank("scout", "Scout").copy(
                requirementVersions = listOf(
                    RequirementsVersion(version, listOf(Requirement("1", "First."), earn))
                )
            )
        ) + ranks.drop(1)
        startCollecting(viewModel)
        assertEquals(0f, ready().nextRank?.fractionDone)

        complete("camping")

        assertEquals(0.5f, ready().nextRank?.fractionDone)
    }

    @Test
    fun badgesInProgress_updateWhenProgressChanges() = runTest {
        startCollecting(viewModel)
        assertEquals(emptyList<String>(), idsInProgress())

        // The first progress on a badge starts it.
        progressRepository.markRequirementCompleted("chess", "1", day, badgeStart)
        assertEquals(listOf("chess"), idsInProgress())
        assertEquals(0.5f, ready().badgesInProgress.single().fractionDone)

        progressRepository.markRequirementCompleted("chess", "2", day, badgeStart)
        assertEquals(emptyList<String>(), idsInProgress())
    }

    @Test
    fun uiState_updatesWhenProfileChanges() = runTest {
        startCollecting(viewModel)

        profileRepository.saveProfile(Profile("Sam Scout", "456"))

        assertEquals("Sam Scout", ready().name)
        assertEquals("456", ready().unitNumber)
    }

    private companion object {
        const val GROUP = "cycling-hiking-swimming"
    }
}
