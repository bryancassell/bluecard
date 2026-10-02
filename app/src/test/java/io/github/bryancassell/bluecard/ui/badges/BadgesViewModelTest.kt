package io.github.bryancassell.bluecard.ui.badges

import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.testing.viewModelScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.catalog.FakeCatalogRepository
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Rank
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.progress.BadgeStart
import io.github.bryancassell.bluecard.data.progress.BadgeStatus
import io.github.bryancassell.bluecard.data.progress.FakeProgressRepository
import io.github.bryancassell.bluecard.testing.MainDispatcherRule
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Robolectric, because the load-failure tests reach android.util.Log, which throws in
// plain local tests (see ARCHITECTURE.md, ViewModel tests).
@RunWith(AndroidJUnit4::class)
class BadgesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val version = LocalDate.of(2026, 1, 1)
    private val started = LocalDate.of(2026, 3, 1)
    private val day = LocalDate.of(2026, 4, 15)
    private val badgeStart = BadgeStart(version, started)

    private fun badge(
        id: String,
        name: String,
        eagleRequired: Boolean = false,
        eagleGroup: String? = null
    ) = MeritBadge(
        id = id,
        name = name,
        summary = "Our summary of $name.",
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

    private val camping = badge("camping", "Camping", eagleRequired = true)
    private val chess = badge("chess", "Chess")

    private val catalogRepository = FakeCatalogRepository(listOf(chess, camping))
    private val progressRepository = FakeProgressRepository()
    private val savedStateHandle = SavedStateHandle()

    // Created in the test, after MainDispatcherRule has replaced the Main dispatcher that
    // viewModelScope uses.
    private val viewModel by lazy {
        BadgesViewModel(catalogRepository, progressRepository, savedStateHandle)
    }

    /**
     * Collects uiState, as the screen does, so WhileSubscribed starts it. From the
     * coroutines testing guide: https://developer.android.com/kotlin/coroutines/test#statein
     */
    private fun TestScope.startCollecting(viewModel: BadgesViewModel) {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }
    }

    private fun item(badgeId: String): BadgeListItem {
        val state = viewModel.uiState.value as BadgesUiState.Ready
        return state.badges.single { it.id == badgeId }
    }

    private fun status(badgeId: String) = item(badgeId).status

    private fun listedIds(): List<String> {
        val state = viewModel.uiState.value as BadgesUiState.Ready
        return state.badges.map { it.id }
    }

    /**
     * Types [text] into the search field, replacing what's there. Compose announces snapshot
     * changes, which snapshotFlow waits for, once per frame; outside a composition, tests
     * announce them.
     */
    private fun search(text: String) {
        viewModel.query.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun uiState_whileCatalogLoads_isLoading() = runTest {
        val loading = object : CatalogRepository {
            override suspend fun getBadges(): List<MeritBadge> = awaitCancellation()
            override suspend fun getRanks(): List<Rank> = awaitCancellation()
        }
        val viewModel = BadgesViewModel(loading, progressRepository, savedStateHandle)
        startCollecting(viewModel)

        assertEquals(BadgesUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun uiState_whenCatalogCantBeRead_isLoadFailed() = runTest {
        catalogRepository.failLoads = true
        startCollecting(viewModel)

        assertEquals(BadgesUiState.LoadFailed, viewModel.uiState.value)
    }

    @Test
    fun uiState_whenProgressCantBeRead_isLoadFailed() = runTest {
        progressRepository.failLoads = true
        startCollecting(viewModel)

        assertEquals(BadgesUiState.LoadFailed, viewModel.uiState.value)
    }

    @Test
    fun uiState_listsEveryBadgeAlphabetically_withEagleFlag() = runTest {
        startCollecting(viewModel)

        assertEquals(
            BadgesUiState.Ready(
                listOf(
                    BadgeListItem(
                        "camping",
                        "Camping",
                        EagleRequirement.Required,
                        BadgeStatus.NotStarted
                    ),
                    BadgeListItem("chess", "Chess", eagle = null, BadgeStatus.NotStarted)
                )
            ),
            viewModel.uiState.value
        )
    }

    @Test
    fun uiState_sortsAlphabetically_ignoringCaseAndAccents() = runTest {
        catalogRepository.badges = listOf(
            badge("z", "Zoology"),
            badge("e", "Écologie"),
            badge("b", "bird Study"),
            badge("a", "Archery")
        )
        startCollecting(viewModel)

        val state = viewModel.uiState.value as BadgesUiState.Ready
        assertEquals(
            listOf("Archery", "bird Study", "Écologie", "Zoology"),
            state.badges.map { it.name }
        )
    }

    @Test
    fun uiState_sortsByEnglishRules_whateverTheDeviceLanguage() = runTest {
        // Czech sorts "ch" as its own letter, after "h".
        val deviceLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("cs"))
        try {
            catalogRepository.badges = listOf(badge("h", "Hiking"), badge("c", "Chess"))
            startCollecting(viewModel)

            val state = viewModel.uiState.value as BadgesUiState.Ready
            assertEquals(listOf("Chess", "Hiking"), state.badges.map { it.name })
        } finally {
            Locale.setDefault(deviceLocale)
        }
    }

    @Test
    fun eagleGroup_isOneOfEveryBadgeInTheGroup() = runTest {
        catalogRepository.badges = listOf(
            badge("swimming", "Swimming", eagleRequired = true, eagleGroup = "c-h-s"),
            badge("cycling", "Cycling", eagleRequired = true, eagleGroup = "c-h-s"),
            badge("hiking", "Hiking", eagleRequired = true, eagleGroup = "c-h-s"),
            camping,
            chess
        )
        startCollecting(viewModel)

        val group = EagleRequirement.OneOf(listOf("Cycling", "Hiking", "Swimming"))
        assertEquals(group, item("cycling").eagle)
        assertEquals(group, item("hiking").eagle)
        assertEquals(group, item("swimming").eagle)
        assertEquals(EagleRequirement.Required, item("camping").eagle)
        assertNull(item("chess").eagle)
    }

    @Test
    fun eagleGroup_withOnlyThisBadgeInCatalog_isRequiredOnItsOwn() = runTest {
        catalogRepository.badges = listOf(
            badge("cycling", "Cycling", eagleRequired = true, eagleGroup = "c-h-s")
        )
        startCollecting(viewModel)

        assertEquals(EagleRequirement.Required, item("cycling").eagle)
    }

    @Test
    fun status_startedBadge_isInProgress() = runTest {
        progressRepository.startBadge("camping", version, started)
        progressRepository.markRequirementCompleted("camping", "1", day, badgeStart)
        startCollecting(viewModel)

        assertEquals(BadgeStatus.InProgress, status("camping"))
        assertEquals(BadgeStatus.NotStarted, status("chess"))
    }

    @Test
    fun status_updatesWhenProgressChanges() = runTest {
        startCollecting(viewModel)
        assertEquals(BadgeStatus.NotStarted, status("camping"))

        progressRepository.startBadge("camping", version, started)
        assertEquals(BadgeStatus.InProgress, status("camping"))

        progressRepository.setCompletedOnPriorDate("camping", day, badgeStart)
        assertEquals(BadgeStatus.Completed, status("camping"))

        progressRepository.clearBadge("camping")
        assertEquals(BadgeStatus.NotStarted, status("camping"))
    }

    @Test
    fun fractionDone_onlyWhileInProgress_updatesWhenProgressChanges() = runTest {
        startCollecting(viewModel)
        assertNull(item("camping").fractionDone)

        progressRepository.startBadge("camping", version, started)
        assertEquals(0f, item("camping").fractionDone)

        progressRepository.markRequirementCompleted("camping", "1", day, badgeStart)
        assertEquals(0.5f, item("camping").fractionDone)

        progressRepository.markRequirementCompleted("camping", "2", day, badgeStart)
        assertEquals(BadgeStatus.Completed, status("camping"))
        assertNull(item("camping").fractionDone)
    }

    @Test
    fun progressForBadgeNotInCatalog_isIgnored() = runTest {
        progressRepository.startBadge("retired-badge", version, started)
        startCollecting(viewModel)

        val state = viewModel.uiState.value as BadgesUiState.Ready
        assertEquals(listOf("camping", "chess"), state.badges.map { it.id })
    }

    @Test
    fun query_startsEmpty_andListsEveryBadge() = runTest {
        startCollecting(viewModel)

        assertEquals("", viewModel.query.text.toString())
        assertEquals(listOf("camping", "chess"), listedIds())
    }

    @Test
    fun search_listsOnlyMatchingBadges() = runTest {
        startCollecting(viewModel)

        search("camp")

        assertEquals(listOf("camping"), listedIds())
    }

    @Test
    fun search_matchesSummaries() = runTest {
        startCollecting(viewModel)

        // Every test badge's summary is "Our summary of <name>."
        search("summary")

        assertEquals(listOf("camping", "chess"), listedIds())
    }

    @Test
    fun search_keepsAlphabeticalOrder() = runTest {
        catalogRepository.badges = listOf(
            badge("cooking", "Cooking"),
            badge("chess", "Chess"),
            badge("camping", "Camping")
        )
        startCollecting(viewModel)

        search("c")

        assertEquals(listOf("camping", "chess", "cooking"), listedIds())
    }

    @Test
    fun search_withNoMatches_isNoMatches() = runTest {
        startCollecting(viewModel)

        search("zoology")

        assertEquals(BadgesUiState.NoMatches, viewModel.uiState.value)
    }

    @Test
    fun emptyCatalog_withBlankSearch_isReadyWithNoBadges() = runTest {
        catalogRepository.badges = emptyList()
        startCollecting(viewModel)

        assertEquals(BadgesUiState.Ready(emptyList()), viewModel.uiState.value)
    }

    @Test
    fun clearingSearch_listsEveryBadgeAgain() = runTest {
        startCollecting(viewModel)
        search("zoology")

        viewModel.query.clearText()
        Snapshot.sendApplyNotifications()

        assertEquals(listOf("camping", "chess"), listedIds())
    }

    @Test
    fun search_keepsFilteringWhenProgressChanges() = runTest {
        startCollecting(viewModel)
        search("camp")

        progressRepository.startBadge("camping", version, started)

        assertEquals(listOf("camping"), listedIds())
        assertEquals(BadgeStatus.InProgress, status("camping"))
    }

    @Test
    fun search_isRestoredFromSavedState() = runTest {
        viewModelScenario {
            BadgesViewModel(catalogRepository, progressRepository, createSavedStateHandle())
        }.use { scenario ->
            // While nothing collects uiState.
            scenario.viewModel.query.setTextAndPlaceCursorAtEnd("camp")

            // Saves state and restores it into a new ViewModel, as when the system stops the
            // app.
            scenario.recreate()
            val restored = scenario.viewModel
            startCollecting(restored)

            assertEquals("camp", restored.query.text.toString())
            val state = restored.uiState.value as BadgesUiState.Ready
            assertEquals(listOf("camping"), state.badges.map { it.id })
        }
    }
}
