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
import io.github.bryancassell.bluecard.ui.badges.EagleRequirement
import java.time.LocalDate
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class BadgeDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val oldest = LocalDate.of(2024, 1, 1)
    private val older = LocalDate.of(2025, 1, 1)
    private val newest = LocalDate.of(2026, 1, 1)
    private val started = LocalDate.of(2026, 3, 1)
    private val day = LocalDate.of(2026, 4, 15)

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
                            listOf(TrackerColumn("night", "Night", TrackerColumnType.DATE))
                        )
                    ),
                    Requirement(
                        "4",
                        "Do all of these.",
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

    // Created in the test, after MainDispatcherRule has replaced the Main dispatcher that
    // viewModelScope uses.
    private fun viewModel(badgeId: String = "camping") =
        BadgeDetailViewModel(badgeId, catalogRepository, progressRepository)

    /**
     * Collects uiState, as the screen does, so WhileSubscribed starts it. From the
     * coroutines testing guide: https://developer.android.com/kotlin/coroutines/test#statein
     */
    private fun TestScope.startCollecting(viewModel: BadgeDetailViewModel) {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }
    }

    private fun BadgeDetailViewModel.ready() = uiState.value as BadgeDetailUiState.Ready

    private fun BadgeDetailViewModel.completed() =
        ready().requirements.orEmpty().associate { it.number to it.completed }

    @Test
    fun uiState_whileCatalogLoads_isLoading() = runTest {
        val loading = object : CatalogRepository {
            override suspend fun getBadges(): List<MeritBadge> = awaitCancellation()
        }
        val viewModel = BadgeDetailViewModel("camping", loading, progressRepository)
        startCollecting(viewModel)

        assertEquals(BadgeDetailUiState.Loading, viewModel.uiState.value)
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
                    RequirementItem("1", "Plan a campout.", null, false, opensDetail = false),
                    RequirementItem(
                        "2",
                        "Do two of these.",
                        Choice(2, 3),
                        false,
                        opensDetail = true
                    ),
                    RequirementItem("3", "Keep a camping log.", null, false, opensDetail = true),
                    RequirementItem("4", "Do all of these.", null, false, opensDetail = true)
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
            listOf(RequirementItem("1", "An older first requirement.", null, false, false)),
            viewModel.ready().requirements
        )
    }

    @Test
    fun startedOnVersionMissingFromCatalog_hasNoRequirements() = runTest {
        progressRepository.startBadge("camping", LocalDate.of(2023, 1, 1), started)
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals("Camping", viewModel.ready().name)
        assertNull(viewModel.ready().requirements)
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
        progressRepository.markRequirementCompleted("camping", "1", day)
        // Two of three is enough for requirement 2.
        progressRepository.markRequirementCompleted("camping", "2a", day)
        progressRepository.markRequirementCompleted("camping", "2c", null)
        // One of two isn't enough for requirement 4.
        progressRepository.markRequirementCompleted("camping", "4a", day)
        val viewModel = viewModel()
        startCollecting(viewModel)

        assertEquals(
            mapOf("1" to true, "2" to true, "3" to false, "4" to false),
            viewModel.completed()
        )
    }

    @Test
    fun uiState_updatesWhenProgressChanges() = runTest {
        progressRepository.startBadge("camping", newest, started)
        val viewModel = viewModel()
        startCollecting(viewModel)
        assertEquals(false, viewModel.completed()["1"])

        progressRepository.markRequirementCompleted("camping", "1", day)
        assertEquals(true, viewModel.completed()["1"])

        progressRepository.markRequirementNotCompleted("camping", "1")
        assertEquals(false, viewModel.completed()["1"])
    }

    @Test
    fun clearingBadgeStartedOnOlderVersion_showsNewestRequirements() = runTest {
        progressRepository.startBadge("camping", older, started)
        val viewModel = viewModel()
        startCollecting(viewModel)
        assertEquals("An older first requirement.", viewModel.ready().requirements!![0].summary)

        progressRepository.clearBadge("camping")

        assertEquals("Plan a campout.", viewModel.ready().requirements!![0].summary)
    }
}
