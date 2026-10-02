package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.FakeCatalogRepository
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.progress.BadgeProgress
import io.github.bryancassell.bluecard.data.progress.FakeProgressRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompletionRecorderTest {
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
        // The newest version isn't the first one listed.
        requirementVersions = listOf(
            RequirementsVersion(older, listOf(Requirement("1", "An older first requirement."))),
            RequirementsVersion(newest, listOf(Requirement("1", "Plan a campout.")))
        )
    )

    private val catalogRepository = FakeCatalogRepository(listOf(camping))
    private val progressRepository = FakeProgressRepository()

    /** A recorder for requirement 1's page, such as one the scout opens. */
    private fun recorder() =
        CompletionRecorder("camping", "1", catalogRepository, progressRepository, clock)

    private suspend fun progress() = progressRepository.observeProgress("camping").first()

    private suspend fun requirement(number: String) =
        progress()?.requirements?.singleOrNull { it.requirementNumber == number }

    @Test
    fun checking_onUnstartedBadge_startsItAndDatesItToday() = runTest {
        recorder().setCompleted(true)

        assertEquals(BadgeProgress("camping", newest, today), progress()?.badge)
        assertEquals(RequirementProgress("camping", "1", true, today), requirement("1"))
    }

    @Test
    fun checking_onStartedBadge_keepsItsVersionAndDate() = runTest {
        progressRepository.startBadge("camping", older, started)

        recorder().setCompleted(true)

        assertEquals(BadgeProgress("camping", older, started), progress()?.badge)
        assertEquals(RequirementProgress("camping", "1", true, today), requirement("1"))
    }

    @Test
    fun unchecking_removesTheDate_andTheBadgeStaysStarted() = runTest {
        val recorder = recorder()
        recorder.setCompleted(true)

        recorder.setCompleted(false)

        assertEquals(RequirementProgress("camping", "1"), requirement("1"))
        assertEquals(BadgeProgress("camping", newest, today), progress()?.badge)
    }

    @Test
    fun checkingAgainOnTheSamePage_bringsBackTheDateItHad() = runTest {
        val recorder = recorder()
        recorder.setCompleted(true)
        progressRepository.setRequirementCompletedDate("camping", "1", day)

        recorder.setCompleted(false)
        recorder.setCompleted(true)

        assertEquals(RequirementProgress("camping", "1", true, day), requirement("1"))
    }

    @Test
    fun checkingAgainOnTheSamePage_bringsBackNoDateForOneThatHadNone() = runTest {
        val recorder = recorder()
        recorder.setCompleted(true)
        progressRepository.setRequirementCompletedDate("camping", "1", null)

        recorder.setCompleted(false)
        recorder.setCompleted(true)

        assertEquals(RequirementProgress("camping", "1", completed = true), requirement("1"))
    }

    @Test
    fun uncheckingTwice_keepsTheDateFromTheFirstTime() = runTest {
        val recorder = recorder()
        recorder.setCompleted(true)
        progressRepository.setRequirementCompletedDate("camping", "1", day)

        // As by a quick double tap, before the checkbox shows the first one.
        recorder.setCompleted(false)
        recorder.setCompleted(false)
        recorder.setCompleted(true)

        assertEquals(RequirementProgress("camping", "1", true, day), requirement("1"))
    }

    @Test
    fun checkingAgainOnAnotherPage_datesItToday() = runTest {
        val closedPage = recorder()
        closedPage.setCompleted(true)
        progressRepository.setRequirementCompletedDate("camping", "1", day)
        closedPage.setCompleted(false)

        recorder().setCompleted(true)

        assertEquals(RequirementProgress("camping", "1", true, today), requirement("1"))
    }

    @Test
    fun checkingWhenSaveFails_keepsTheDateToBringBack() = runTest {
        val recorder = recorder()
        recorder.setCompleted(true)
        progressRepository.setRequirementCompletedDate("camping", "1", day)
        recorder.setCompleted(false)
        progressRepository.failSaves = true

        runCatching { recorder.setCompleted(true) }
        progressRepository.failSaves = false
        recorder.setCompleted(true)

        assertEquals(RequirementProgress("camping", "1", true, day), requirement("1"))
    }

    @Test
    fun clear_removesWhatWasRecorded_andCheckingAgainDatesItToday() = runTest {
        val recorder = recorder()
        recorder.setCompleted(true)
        progressRepository.setRequirementCompletedDate("camping", "1", day)
        recorder.setCompleted(false)

        recorder.clear(listOf("1"))
        assertNull(requirement("1"))
        recorder.setCompleted(true)

        assertEquals(RequirementProgress("camping", "1", true, today), requirement("1"))
    }

    @Test
    fun checkingWhileAClearIsBeingSaved_datesItToday() = runTest {
        val clearSaved = CompletableDeferred<Unit>()
        // Saves a clear only once it's let through, as Room takes a moment to.
        val slowClears = object : ProgressRepository by progressRepository {
            override suspend fun clearRequirements(badgeId: String, numbers: Collection<String>) {
                clearSaved.await()
                progressRepository.clearRequirements(badgeId, numbers)
            }
        }
        val recorder = CompletionRecorder("camping", "1", catalogRepository, slowClears, clock)
        recorder.setCompleted(true)
        progressRepository.setRequirementCompletedDate("camping", "1", day)
        recorder.setCompleted(false)

        launch { recorder.clear(listOf("1")) }
        runCurrent()
        recorder.setCompleted(true)

        assertEquals(RequirementProgress("camping", "1", true, today), requirement("1"))
        clearSaved.complete(Unit)
    }

    @Test
    fun unchecking_afterTheBadgeIsCleared_doesNothing() = runTest {
        val recorder = recorder()
        recorder.setCompleted(true)
        progressRepository.clearBadge("camping")

        // As when the page still shows the requirement checked.
        recorder.setCompleted(false)

        assertNull(progress())
    }
}
