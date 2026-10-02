package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.FakeCatalogRepository
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.progress.BadgeStart
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeStartTest {
    private val older = LocalDate.of(2025, 1, 1)
    private val newest = LocalDate.of(2026, 1, 1)
    private val today = LocalDate.of(2026, 5, 20)
    private val clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    private val catalogRepository = FakeCatalogRepository(
        listOf(
            MeritBadge(
                id = "camping",
                name = "Camping",
                summary = "Our summary of Camping.",
                officialUrl = "https://www.scouting.org/merit-badges/camping/",
                // The newest version isn't the first one listed.
                requirementVersions = listOf(
                    RequirementsVersion(older, listOf(Requirement("1", "An older requirement."))),
                    RequirementsVersion(newest, listOf(Requirement("1", "Plan a campout.")))
                )
            )
        )
    )

    @Test
    fun badgeStart_isNewestVersionToday() = runTest {
        assertEquals(BadgeStart(newest, today), catalogRepository.badgeStart("camping", clock))
    }

    @Test
    fun badgeStart_forBadgeNotInCatalog_fails() = runTest {
        val error = runCatching { catalogRepository.badgeStart("archery", clock) }.exceptionOrNull()

        assertTrue("Expected an IllegalStateException, got $error", error is IllegalStateException)
    }
}
