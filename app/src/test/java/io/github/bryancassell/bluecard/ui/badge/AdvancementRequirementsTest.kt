package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.Advancement
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Rank
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.progress.BadgeProgress
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import io.github.bryancassell.bluecard.data.progress.TrackerEntry
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancementRequirementsTest {
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
            AdvancementRequirements(camping, newest, emptyMap(), emptyMap()),
            catalog.advancementRequirements("camping", null)
        )
    }

    @Test
    fun completedOnPriorDate_isTold_andItsRequirementsAreNotRecorded() {
        val started = startedOn(newest.effectiveDate)
        assertFalse(catalog.advancementRequirements("camping", started)!!.completedOnPriorDate)

        val marked = started.copy(
            badge = started.badge.copy(completedOnPriorDate = LocalDate.of(2025, 8, 1))
        )
        val found = catalog.advancementRequirements("camping", marked)!!

        assertTrue(found.completedOnPriorDate)
        assertTrue(found.item(newest.requirements.first()).notRecorded)
    }

    @Test
    fun started_isVersionItWasStartedOn_withProgressByNumber() {
        val found = catalog.advancementRequirements("camping", startedOn(older.effectiveDate, "1"))

        assertEquals(older, found?.version)
        assertEquals(setOf("1"), found?.recorded?.keys)
    }

    @Test
    fun started_hasTrackerEntriesByNumber() {
        val first = TrackerEntry(1, "camping", "2a", values = mapOf("meal" to "Chili"))
        val second = TrackerEntry(2, "camping", "2a", values = mapOf("meal" to "Stew"))
        val other = TrackerEntry(3, "camping", "1", values = mapOf("site" to "Lake"))
        val progress = startedOn(
            newest.effectiveDate
        ).copy(trackerEntries = listOf(first, other, second))

        assertEquals(
            mapOf("2a" to listOf(first, second), "1" to listOf(other)),
            catalog.advancementRequirements("camping", progress)?.trackerEntries
        )
    }

    @Test
    fun item_isRequirementWithWhatWasRecorded() {
        val found = catalog.advancementRequirements(
            "camping",
            startedOn(newest.effectiveDate, "2a")
        )!!

        assertEquals(
            RequirementItem("2", "Do one of these.", Choice(1, 2), true, markedByHand = false),
            found.item(newest.requirements[1])
        )
    }

    @Test
    fun item_partOfCompletedRequirement_isNotNeeded() {
        // 2a completes 2, which needs one of its two choices.
        val found = catalog.advancementRequirements(
            "camping",
            startedOn(newest.effectiveDate, "2a")
        )!!

        assertTrue(found.item(newest.find("2b")!!).notNeeded)
        assertTrue(found.item(newest.find("2b(1)")!!).notNeeded)
        // Complete itself.
        assertFalse(found.item(newest.find("2a")!!).notNeeded)
        // Top-level: part of no requirement.
        assertFalse(found.item(newest.find("1")!!).notNeeded)
    }

    @Test
    fun item_partOfRequirementWithEnoughDone_isNotNeeded_beforeItsOwnWorkIs() {
        val withOwnWork = newest.copy(
            requirements = newest.requirements.map {
                if (it.number == "2") it.copy(ownWork = "Share what you did.") else it
            }
        )
        val found = listOf(badge("camping", withOwnWork))
            .advancementRequirements("camping", startedOn(withOwnWork.effectiveDate, "2a"))!!

        assertFalse(found.item(withOwnWork.find("2")!!).completed)
        assertTrue(found.item(withOwnWork.find("2b")!!).notNeeded)
        assertTrue(found.item(withOwnWork.find("2b(1)")!!).notNeeded)
    }

    @Test
    fun item_beforeARequirementItsPartOfIsComplete_isNeeded() {
        val found = catalog.advancementRequirements("camping", null)!!

        assertFalse(found.item(newest.find("2b(1)")!!).notNeeded)
    }

    @Test
    fun rank_isFoundAmongTheBadges() {
        val tenderfoot = Rank(
            id = "tenderfoot",
            name = "Tenderfoot",
            summary = "Our summary.",
            officialUrl = "https://www.scouting.org/tenderfoot/",
            requirementVersions = listOf(newest)
        )
        val advancements: List<Advancement> = catalog + tenderfoot

        assertEquals(
            AdvancementRequirements(tenderfoot, newest, emptyMap(), emptyMap()),
            advancements.advancementRequirements("tenderfoot", null)
        )
    }

    @Test
    fun badgeMissingFromCatalog_isNull() {
        assertNull(catalog.advancementRequirements("retired-badge", null))
    }

    @Test
    fun versionMissingFromCatalog_isNull() {
        assertNull(catalog.advancementRequirements("camping", startedOn(LocalDate.of(2024, 1, 1))))
    }

    @Test
    fun hasRecorded_completionCommentOrTrackerEntry_ofAnyOfTheNumbers() {
        val progress = startedOn(newest.effectiveDate, "2a").copy(
            requirements = listOf(
                RequirementProgress("camping", "2a", completed = true),
                RequirementProgress("camping", "2b", comment = "Picked the lake trail."),
                // Unchecked, so nothing shows.
                RequirementProgress("camping", "1")
            ),
            trackerEntries = listOf(
                TrackerEntry(1, "camping", "2b(1)", values = mapOf("miles" to "5"))
            )
        )
        val found = catalog.advancementRequirements("camping", progress)!!

        assertTrue(found.hasRecorded(listOf("1", "2a")))
        assertTrue(found.hasRecorded(listOf("2b")))
        assertTrue(found.hasRecorded(listOf("2b(1)")))
        assertFalse(found.hasRecorded(listOf("1", "2")))
    }

    @Test
    fun numbersWithin_isTheRequirementAndEveryOneUnderIt() {
        assertEquals(listOf("2", "2a", "2b", "2b(1)"), newest.find("2")!!.numbersWithin())
        assertEquals(listOf("1"), newest.find("1")!!.numbersWithin())
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
