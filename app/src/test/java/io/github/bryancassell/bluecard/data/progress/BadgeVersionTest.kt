package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.catalog.FakeCatalogRepository
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeVersionTest {
    private val oldest =
        RequirementsVersion(LocalDate.of(2024, 1, 1), listOf(Requirement("1", "A.")))
    private val older =
        RequirementsVersion(LocalDate.of(2025, 1, 1), listOf(Requirement("1", "B.")))
    private val newest =
        RequirementsVersion(LocalDate.of(2026, 1, 1), listOf(Requirement("1", "C.")))

    private val badge = MeritBadge(
        id = BADGE,
        name = "Camping",
        summary = "Our summary of Camping.",
        officialUrl = "https://www.scouting.org/merit-badges/camping/",
        // The newest version is neither the first nor the last one listed.
        requirementVersions = listOf(older, newest, oldest)
    )

    private fun startedOn(version: LocalDate) = BadgeProgressDetails(
        BadgeProgress(BADGE, version, startedDate = LocalDate.of(2026, 3, 1)),
        emptyList(),
        emptyList()
    )

    @Test
    fun notStarted_isNewestVersion() {
        assertEquals(newest, badge.requirementsVersionFor(null))
    }

    @Test
    fun started_isVersionItWasStartedOn() {
        assertEquals(older, badge.requirementsVersionFor(startedOn(older.effectiveDate)))
    }

    @Test
    fun noVersionsInCatalog_isNull() {
        assertNull(badge.copy(requirementVersions = emptyList()).requirementsVersionFor(null))
    }

    @Test
    fun startedOnVersionMissingFromCatalog_isNull() {
        assertNull(badge.requirementsVersionFor(startedOn(LocalDate.of(2023, 1, 1))))
    }

    @Test
    fun badgeStart_isNewestVersionOnTheDayGiven() = runTest {
        val today = LocalDate.of(2026, 5, 20)

        val start = FakeCatalogRepository(listOf(badge)).badgeStart(BADGE, today)

        assertEquals(BadgeStart(newest.effectiveDate, today), start)
    }

    @Test
    fun badgeStart_forBadgeNotInCatalog_fails() = runTest {
        val catalog = FakeCatalogRepository(listOf(badge))

        val error = runCatching { catalog.badgeStart("archery", LocalDate.of(2026, 5, 20)) }
            .exceptionOrNull()

        assertTrue("Expected an IllegalStateException, got $error", error is IllegalStateException)
    }

    private companion object {
        const val BADGE = "camping"
    }
}
