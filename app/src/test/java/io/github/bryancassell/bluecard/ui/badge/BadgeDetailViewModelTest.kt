package io.github.bryancassell.bluecard.ui.badge

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
import io.github.bryancassell.bluecard.data.progress.BadgeStatus
import io.github.bryancassell.bluecard.data.progress.FakeProgressRepository
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import io.github.bryancassell.bluecard.data.progress.status
import io.github.bryancassell.bluecard.testing.MainDispatcherRule
import io.github.bryancassell.bluecard.ui.badges.EagleRequirement
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Robolectric, because the load-failure tests reach android.util.Log, which throws in
// plain local tests (see ARCHITECTURE.md, Testing approach).
@RunWith(AndroidJUnit4::class)
class BadgeDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val oldest = LocalDate.of(2024, 1, 1)
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

    // Created in the test, after MainDispatcherRule has replaced the Main dispatcher that
    // viewModelScope uses.
    private fun viewModel(badgeId: String = "camping") =
        BadgeDetailViewModel(badgeId, catalogRepository, progressRepository, clock)

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
        }
        val viewModel = BadgeDetailViewModel("camping", loading, progressRepository, clock)
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
                    RequirementItem("1", "Plan a campout.", null, false, false),
                    RequirementItem("2", "Do two of these.", Choice(2, 3), false, true),
                    // The scout marks a requirement with a tracker complete themselves.
                    RequirementItem("3", "Keep a camping log.", null, false, false),
                    RequirementItem("4", "Do all of these.", null, false, true)
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
        assertEquals("An older first requirement.", viewModel.ready().requirements[0].summary)

        progressRepository.clearBadge("camping")

        assertEquals("Plan a campout.", viewModel.ready().requirements[0].summary)
    }

    @Test
    fun setCompleted_onUnstartedBadge_startsItOnNewestVersionAndCompletesItToday() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)

        viewModel.setCompleted("1", true)

        val progress = progressRepository.observeProgress("camping").first()!!
        assertEquals(BadgeProgress("camping", newest, today), progress.badge)
        assertEquals(
            listOf(RequirementProgress("camping", "1", completed = true, completedDate = today)),
            progress.requirements
        )
        assertEquals(true, viewModel.completed()["1"])
    }

    @Test
    fun setCompleted_onBadgeStartedOnOlderVersion_keepsThatVersion() = runTest {
        progressRepository.startBadge("camping", older, started)
        val viewModel = viewModel()
        startCollecting(viewModel)

        viewModel.setCompleted("1", true)

        assertEquals(
            BadgeProgress("camping", older, started),
            progressRepository.observeProgress("camping").first()!!.badge
        )
        assertEquals(mapOf("1" to true), viewModel.completed())
    }

    @Test
    fun setCompleted_false_undoesIt() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)
        viewModel.setCompleted("1", true)

        viewModel.setCompleted("1", false)

        assertEquals(false, viewModel.completed()["1"])
        // The badge stays started.
        assertNotNull(progressRepository.observeProgress("camping").first())
    }

    @Test
    fun uncheckingThenChecking_onThisPage_keepsTheDate() = runTest {
        progressRepository.startBadge("camping", newest, started)
        progressRepository.markRequirementCompleted("camping", "1", day)
        val viewModel = viewModel()
        startCollecting(viewModel)

        viewModel.setCompleted("1", false)
        viewModel.setCompleted("1", true)

        assertEquals(
            listOf(RequirementProgress("camping", "1", completed = true, completedDate = day)),
            progressRepository.observeProgress("camping").first()!!.requirements
        )
    }

    @Test
    fun completingAllRequirements_completesEveryRequirement() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)

        listOf("1", "2a", "2b", "3", "4a", "4b").forEach { viewModel.setCompleted(it, true) }

        assertEquals(
            mapOf("1" to true, "2" to true, "3" to true, "4" to true),
            viewModel.completed()
        )
        val progress = progressRepository.observeProgress("camping").first()
        assertEquals(BadgeStatus.Completed, camping.status(progress))
    }

    @Test
    fun setCompleted_whenSaveFails_reportsItUntilShown() = runTest {
        val viewModel = viewModel()
        startCollecting(viewModel)
        assertNull(viewModel.ready().saveFailure)
        progressRepository.failSaves = true

        viewModel.setCompleted("1", true)

        val failure = viewModel.ready().saveFailure
        assertNotNull(failure)
        assertEquals(false, viewModel.completed()["1"])
        // Starting the badge failed with the rest.
        progressRepository.failSaves = false
        assertNull(progressRepository.observeProgress("camping").first())

        viewModel.onSaveFailureShown(failure!!)

        assertNull(viewModel.ready().saveFailure)
    }
}
