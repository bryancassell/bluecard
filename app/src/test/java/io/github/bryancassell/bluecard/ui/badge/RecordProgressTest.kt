package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.progress.BadgeProgress
import io.github.bryancassell.bluecard.data.progress.BadgeStart
import io.github.bryancassell.bluecard.data.progress.FakeProgressRepository
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordProgressTest {
    private val older = LocalDate.of(2025, 1, 1)
    private val newest = LocalDate.of(2026, 1, 1)
    private val started = LocalDate.of(2026, 3, 1)
    private val today = LocalDate.of(2026, 4, 15)

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

    private val repository = FakeProgressRepository()

    private suspend fun progress() = repository.observeProgress("camping").first()

    private suspend fun requirement(number: String) =
        progress()?.requirements?.singleOrNull { it.requirementNumber == number }

    @Test
    fun startedOn_isNewestVersionOnThatDay() {
        assertEquals(BadgeStart(newest, today), camping.startedOn(today))
    }

    @Test
    fun setRequirementCompleted_onStartedBadge_keepsItsVersionAndDate() = runTest {
        repository.startBadge("camping", older, started)

        repository.setRequirementCompleted(camping, "1", completed = true, today)

        assertEquals(BadgeProgress("camping", older, started), progress()?.badge)
        assertEquals(RequirementProgress("camping", "1", true, today), requirement("1"))
    }

    @Test
    fun setRequirementCompleted_notStarted_startsBadgeAndDatesItToday() = runTest {
        repository.setRequirementCompleted(camping, "1", completed = true, today)

        assertEquals(BadgeProgress("camping", newest, today), progress()?.badge)
        assertEquals(RequirementProgress("camping", "1", true, today), requirement("1"))
    }

    @Test
    fun setRequirementCompleted_false_undoesIt() = runTest {
        repository.setRequirementCompleted(camping, "1", completed = true, today)

        repository.setRequirementCompleted(camping, "1", completed = false, today)

        assertEquals(RequirementProgress("camping", "1"), requirement("1"))
        // The badge stays started.
        assertEquals(BadgeProgress("camping", newest, today), progress()?.badge)
    }
}
