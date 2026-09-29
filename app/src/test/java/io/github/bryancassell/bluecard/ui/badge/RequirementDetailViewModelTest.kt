package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.catalog.FakeCatalogRepository
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition
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

class RequirementDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val older = LocalDate.of(2025, 1, 1)
    private val newest = LocalDate.of(2026, 1, 1)
    private val started = LocalDate.of(2026, 3, 1)
    private val day = LocalDate.of(2026, 4, 15)

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

    // Created in the test, after MainDispatcherRule has replaced the Main dispatcher that
    // viewModelScope uses.
    private fun viewModel(number: String) =
        RequirementDetailViewModel("camping", number, catalogRepository, progressRepository)

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
        val viewModel = RequirementDetailViewModel("camping", "2", loading, progressRepository)
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
                children = listOf(
                    RequirementItem("2a", "Cook a meal.", null, false, opensDetail = false),
                    RequirementItem(
                        "2b",
                        "Lead one hike.",
                        Choice(1, 2),
                        false,
                        opensDetail = true
                    ),
                    RequirementItem("2c", "Keep a camping log.", null, false, opensDetail = false)
                )
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
        val viewModel =
            RequirementDetailViewModel("retired-badge", "2", catalogRepository, progressRepository)
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
}
