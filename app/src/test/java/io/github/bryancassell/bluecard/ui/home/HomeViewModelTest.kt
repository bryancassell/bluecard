package io.github.bryancassell.bluecard.ui.home

import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.catalog.FakeCatalogRepository
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.profile.FakeProfileRepository
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.progress.FakeProgressRepository
import io.github.bryancassell.bluecard.testing.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val version = LocalDate.of(2026, 1, 1)
    private val started = LocalDate.of(2026, 3, 1)
    private val day = LocalDate.of(2026, 4, 15)

    private fun badge(id: String, eagleRequired: Boolean = false, eagleGroup: String? = null) =
        MeritBadge(
            id = id,
            name = id,
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

    private val profileRepository = FakeProfileRepository(Profile("Alex Scout", "123"))
    private val catalogRepository = FakeCatalogRepository(catalog)
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

    private suspend fun start(badgeId: String) {
        progressRepository.startBadge(badgeId, version, started)
    }

    private suspend fun complete(badgeId: String) {
        start(badgeId)
        progressRepository.setCompletedOnPriorDate(badgeId, day)
    }

    @Test
    fun uiState_whileCatalogLoads_isLoading() = runTest {
        val loading = object : CatalogRepository {
            override suspend fun getBadges(): List<MeritBadge> = awaitCancellation()
        }
        val viewModel = HomeViewModel(profileRepository, loading, progressRepository)
        startCollecting(viewModel)

        assertEquals(HomeUiState.Loading, viewModel.uiState.value)
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
                badges = ProgressCounts(completed = 0, inProgress = 0),
                eagle = ProgressCounts(completed = 0, inProgress = 0),
                eagleTotal = 3
            ),
            viewModel.uiState.value
        )
        assertTrue(ready().hasNoProgress)
    }

    @Test
    fun badges_countsCompletedAndInProgress() = runTest {
        start("chess")
        start("camping")
        progressRepository.markRequirementCompleted("camping", "1", day)
        complete("pottery")
        // Completed through its requirements rather than a prior date.
        start("cooking")
        progressRepository.markRequirementCompleted("cooking", "1", day)
        progressRepository.markRequirementCompleted("cooking", "2", null)
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

        progressRepository.setCompletedOnPriorDate("camping", day)
        assertEquals(ProgressCounts(completed = 1, inProgress = 0), ready().badges)
        assertEquals(ProgressCounts(completed = 1, inProgress = 0), ready().eagle)

        progressRepository.clearAll()
        assertTrue(ready().hasNoProgress)
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
