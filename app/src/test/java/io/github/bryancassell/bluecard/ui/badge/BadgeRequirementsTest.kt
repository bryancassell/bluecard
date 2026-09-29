package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.progress.BadgeProgress
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BadgeRequirementsTest {
    private val older = RequirementsVersion(
        LocalDate.of(2025, 1, 1),
        listOf(Requirement("1", "An older first requirement."))
    )
    private val newest = RequirementsVersion(
        LocalDate.of(2026, 1, 1),
        listOf(
            Requirement("1", "Plan a campout."),
            Requirement(
                "2",
                "Do one of these.",
                requiredCount = 1,
                children = listOf(
                    Requirement("2a", "Cook a meal."),
                    Requirement(
                        "2b",
                        "Lead a hike.",
                        children = listOf(Requirement("2b(1)", "Deep."))
                    )
                )
            )
        )
    )

    private fun badge(id: String, vararg versions: RequirementsVersion) = MeritBadge(
        id = id,
        name = id.replaceFirstChar { it.uppercase() },
        summary = "Our summary.",
        officialUrl = "https://www.scouting.org/merit-badges/$id/",
        requirementVersions = versions.toList()
    )

    private val camping = badge("camping", older, newest)
    private val catalog = listOf(badge("chess", newest), camping)

    private fun startedOn(version: LocalDate, vararg done: String) = BadgeProgressDetails(
        BadgeProgress("camping", version, startedDate = LocalDate.of(2026, 3, 1)),
        done.map { RequirementProgress("camping", it, completed = true) },
        emptyList()
    )

    @Test
    fun notStarted_isBadgeOnNewestVersion_withNothingRecorded() {
        assertEquals(
            BadgeRequirements(camping, newest, emptyMap()),
            catalog.badgeRequirements("camping", null)
        )
    }

    @Test
    fun started_isVersionItWasStartedOn_withProgressByNumber() {
        val found = catalog.badgeRequirements("camping", startedOn(older.effectiveDate, "1"))

        assertEquals(older, found?.version)
        assertEquals(setOf("1"), found?.recorded?.keys)
    }

    @Test
    fun badgeMissingFromCatalog_isNull() {
        assertNull(catalog.badgeRequirements("retired-badge", null))
    }

    @Test
    fun versionMissingFromCatalog_isNull() {
        assertNull(catalog.badgeRequirements("camping", startedOn(LocalDate.of(2024, 1, 1))))
    }

    @Test
    fun find_topLevelRequirement() {
        assertEquals("Plan a campout.", newest.find("1")?.summary)
    }

    @Test
    fun find_requirementThreeLevelsDown() {
        assertEquals("Deep.", newest.find("2b(1)")?.summary)
    }

    @Test
    fun find_numberNotInVersion_isNull() {
        assertNull(newest.find("3"))
    }
}
