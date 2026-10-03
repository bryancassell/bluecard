package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BadgeStatusTest {
    private val oldVersion = LocalDate.of(2025, 1, 1)
    private val newVersion = LocalDate.of(2026, 1, 1)
    private val day = LocalDate.of(2026, 4, 15)

    // The old version needs only requirement 1; the new one needs 1 and 2.
    private val badge = MeritBadge(
        id = BADGE,
        name = "Camping",
        summary = "Our summary of Camping.",
        officialUrl = "https://www.scouting.org/merit-badges/camping/",
        requirementVersions = listOf(
            RequirementsVersion(oldVersion, listOf(Requirement("1", "First."))),
            RequirementsVersion(
                newVersion,
                listOf(Requirement("1", "First."), Requirement("2", "Second."))
            )
        )
    )

    private fun progress(
        version: LocalDate,
        vararg done: String,
        completedOnPriorDate: LocalDate? = null
    ) = BadgeProgressDetails(
        BadgeProgress(BADGE, version, day, completedOnPriorDate = completedOnPriorDate),
        done.map { RequirementProgress(BADGE, it, completed = true, completedDate = day) },
        emptyList()
    )

    @Test
    fun notStarted_whenNoProgress() {
        assertEquals(BadgeStatus.NotStarted, badge.status(null))
    }

    @Test
    fun inProgress_untilEveryRequirementIsDone() {
        assertEquals(BadgeStatus.InProgress, badge.status(progress(newVersion)))
        assertEquals(BadgeStatus.InProgress, badge.status(progress(newVersion, "1")))
    }

    @Test
    fun completed_whenEveryRequirementIsDone() {
        assertEquals(BadgeStatus.Completed, badge.status(progress(newVersion, "1", "2")))
    }

    @Test
    fun checksTheVersionTheBadgeWasStartedOn() {
        // Requirement 1 completes the old version but not the new one.
        assertEquals(BadgeStatus.Completed, badge.status(progress(oldVersion, "1")))
        assertEquals(BadgeStatus.InProgress, badge.status(progress(newVersion, "1")))
    }

    @Test
    fun completed_whenMarkedCompletedOnPriorDate() {
        assertEquals(
            BadgeStatus.Completed,
            badge.status(progress(newVersion, completedOnPriorDate = day))
        )
    }

    @Test
    fun versionMissingFromCatalog_isInProgress() {
        assertEquals(
            BadgeStatus.InProgress,
            badge.status(progress(LocalDate.of(2024, 1, 1), "1", "2"))
        )
    }

    @Test
    fun versionMissingFromCatalog_completedOnPriorDate_isCompleted() {
        assertEquals(
            BadgeStatus.Completed,
            badge.status(progress(LocalDate.of(2024, 1, 1), completedOnPriorDate = day))
        )
    }

    @Test
    fun completion_isNull_untilEveryRequirementIsDone() {
        assertNull(badge.completion(null))
        assertNull(badge.completion(progress(newVersion, "1")))
    }

    @Test
    fun completion_isOnTheDateTheLastRequirementWasDone() {
        assertEquals(Completion(day), badge.completion(progress(newVersion, "1", "2")))
    }

    @Test
    fun completion_whenMarkedCompletedOnPriorDate_isOnThatDate() {
        val prior = LocalDate.of(2025, 6, 1)

        assertEquals(
            Completion(prior),
            badge.completion(progress(LocalDate.of(2024, 1, 1), completedOnPriorDate = prior))
        )
    }

    @Test
    fun completion_onAVersionMissingFromCatalog_isNull() {
        assertNull(badge.completion(progress(LocalDate.of(2024, 1, 1), "1", "2")))
    }

    private companion object {
        const val BADGE = "camping"
    }
}
