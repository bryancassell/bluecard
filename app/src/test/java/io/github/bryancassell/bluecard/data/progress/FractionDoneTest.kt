package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FractionDoneTest {
    private fun leaf(number: String) = Requirement(number = number, summary = "Do $number.")

    private fun done(number: String) = RequirementProgress(BADGE, number, completed = true)

    private fun progressOf(vararg progress: RequirementProgress) =
        progress.associateBy { it.requirementNumber }

    /** How much is done from requirement progress alone, with no tracker entries. */
    private fun Requirement.fractionDone(vararg progress: RequirementProgress) =
        fractionDone(progressOf(*progress), emptyMap())

    // Requirement 2 needs both children; requirement 3 needs any two of three.
    private val allOf = Requirement(
        number = "2",
        summary = "Do both.",
        children = listOf(leaf("2a"), leaf("2b"))
    )
    private val twoOf = Requirement(
        number = "3",
        summary = "Do two.",
        requiredCount = 2,
        children = listOf(leaf("3a"), leaf("3b"), leaf("3c"))
    )

    @Test
    fun leaf_doneOnlyWhenMarked() {
        assertEquals(0f, leaf("1").fractionDone())
        val started = RequirementProgress(BADGE, "1", comment = "Started")
        assertEquals(0f, leaf("1").fractionDone(started))
        assertEquals(1f, leaf("1").fractionDone(done("1")))
    }

    @Test
    fun allChildrenRequired_countsEachChild() {
        assertEquals(0f, allOf.fractionDone())
        assertEquals(0.5f, allOf.fractionDone(done("2b")))
        assertEquals(1f, allOf.fractionDone(done("2a"), done("2b")))
    }

    @Test
    fun choice_countsOnlyTheNumberNeeded() {
        assertEquals(0.5f, twoOf.fractionDone(done("3c")))
        assertEquals(1f, twoOf.fractionDone(done("3a"), done("3c")))
        // A third choice adds nothing once two are done.
        assertEquals(1f, twoOf.fractionDone(done("3a"), done("3b"), done("3c")))
    }

    @Test
    fun choice_countsTheFurthestAlongChildren() {
        // 4b is half done and 4c a quarter; 4a, untouched, doesn't count against them.
        val choice = Requirement(
            number = "4",
            summary = "Do two.",
            requiredCount = 2,
            children = listOf(
                leaf("4a"),
                allOf.copy(number = "4b", children = listOf(leaf("4b1"), leaf("4b2"))),
                allOf.copy(
                    number = "4c",
                    children = listOf(leaf("4c1"), leaf("4c2"), leaf("4c3"), leaf("4c4"))
                )
            )
        )
        assertEquals((0.5f + 0.25f) / 2, choice.fractionDone(done("4b1"), done("4c1")))
    }

    @Test
    fun nestedChildren_countByHowMuchOfThemIsDone() {
        // 7a has two parts, one done, so it counts as half of one of requirement 7's two parts.
        val nested = Requirement(
            number = "7",
            summary = "Do both.",
            children = listOf(
                Requirement(
                    number = "7a",
                    summary = "Do both.",
                    children = listOf(leaf("7a(1)"), leaf("7a(2)"))
                ),
                leaf("7b")
            )
        )
        assertEquals(0.25f, nested.fractionDone(done("7a(1)")))
        assertEquals(0.75f, nested.fractionDone(done("7a(1)"), done("7b")))
    }

    // Requirement 3 with work of its own besides two of its three children.
    private val ownWorkAndTwoOf = twoOf.copy(ownWork = "Explain it.")

    @Test
    fun ownWork_countsAsOneMorePart() {
        assertEquals(1f / 3, ownWorkAndTwoOf.fractionDone(done("3")))
        assertEquals(2f / 3, ownWorkAndTwoOf.fractionDone(done("3a"), done("3b")))
        assertEquals(1f, ownWorkAndTwoOf.fractionDone(done("3"), done("3a"), done("3b")))
    }

    @Test
    fun withoutOwnWork_markingItDoesNothing() {
        // Only one with own work has a checkbox of its own; a stray mark isn't progress.
        assertEquals(0f, allOf.fractionDone(done("2")))
    }

    // Requirement 5 is a tracker with four rows, and requirement 6 a log.
    private val income = listOf(TrackerColumn("income", "Income", TrackerColumnType.NUMBER))
    private val weeks = Requirement(
        number = "5",
        summary = "Track four weeks.",
        tracker = TrackerDefinition(income, "week", "weeks", rowCount = 4)
    )
    private val log = Requirement(
        number = "6",
        summary = "Keep a log.",
        tracker = TrackerDefinition(income, "session", "sessions")
    )

    private fun row(number: String, rowNumber: Int?) =
        TrackerEntry(0, BADGE, number, rowNumber, mapOf("income" to "10"), LocalDate.of(2026, 5, 1))

    private fun entriesOf(vararg entries: TrackerEntry) = entries.groupBy { it.requirementNumber }

    @Test
    fun fixedRows_countFilledRows() {
        assertEquals(0f, weeks.fractionDone(emptyMap(), emptyMap()))
        assertEquals(0.5f, weeks.fractionDone(emptyMap(), entriesOf(row("5", 1), row("5", 3))))
        val allRows = entriesOf(row("5", 1), row("5", 2), row("5", 3), row("5", 4))
        assertEquals(1f, weeks.fractionDone(emptyMap(), allRows))
    }

    @Test
    fun fixedRows_ignoreRowsOutsideTheTracker() {
        // Only a catalog edited during development could leave row 9 in a four-row tracker.
        assertEquals(0.25f, weeks.fractionDone(emptyMap(), entriesOf(row("5", 1), row("5", 9))))
    }

    @Test
    fun log_doneOnlyWhenMarked() {
        val entries = entriesOf(row("6", null), row("6", null))
        assertEquals(0f, log.fractionDone(emptyMap(), entries))
        assertEquals(1f, log.fractionDone(progressOf(done("6")), entries))
    }

    @Test
    fun childrenWithATracker_countChildrenOnly() {
        // As for completion, its children decide, not its tracker's rows.
        val withTracker = allOf.copy(tracker = weeks.tracker)
        assertEquals(
            0.5f,
            withTracker.fractionDone(progressOf(done("2a")), entriesOf(row("2", 1), row("2", 2)))
        )
    }

    @Test
    fun isOneExactlyWhenComplete() {
        val requirements = listOf(leaf("1"), allOf, twoOf, ownWorkAndTwoOf.copy(number = "4"))
        val progressSets = listOf(
            progressOf(),
            progressOf(done("1"), done("2a"), done("3a")),
            progressOf(done("2a"), done("2b"), done("3b"), done("3c")),
            progressOf(done("4"), done("3a"), done("3b"))
        )
        for (requirement in requirements) {
            for (progress in progressSets) {
                val complete = requirement.completion(progress, emptyMap()) != null
                val fraction = requirement.fractionDone(progress, emptyMap())
                assertEquals("${requirement.number} with $progress", complete, fraction == 1f)
                assertTrue(fraction in 0f..1f)
            }
        }
    }

    private val version = RequirementsVersion(
        LocalDate.of(2026, 1, 1),
        listOf(leaf("1"), allOf, twoOf, weeks)
    )

    private fun badge(
        vararg progress: RequirementProgress,
        completedOnPriorDate: LocalDate? = null,
        trackerEntries: List<TrackerEntry> = emptyList()
    ) = BadgeProgressDetails(
        BadgeProgress(
            BADGE,
            version.effectiveDate,
            version.effectiveDate,
            completedOnPriorDate = completedOnPriorDate
        ),
        progress.toList(),
        trackerEntries
    )

    @Test
    fun badge_nothingRecorded_isNothingDone() {
        assertEquals(0f, badge().fractionDone(version))
    }

    @Test
    fun badge_averagesItsTopLevelRequirements() {
        // 1 all done, 2 half, 3 half and 5 three quarters: (1 + 0.5 + 0.5 + 0.75) / 4.
        val partly = badge(
            done("1"),
            done("2a"),
            done("3b"),
            trackerEntries = listOf(row("5", 1), row("5", 2), row("5", 3))
        )
        assertEquals(0.6875f, partly.fractionDone(version))
    }

    @Test
    fun badge_allRequirementsComplete_isAllDone() {
        val all = badge(
            done("1"),
            done("2a"),
            done("2b"),
            done("3a"),
            done("3c"),
            trackerEntries = (1..4).map { row("5", it) }
        )
        assertEquals(1f, all.fractionDone(version))
    }

    @Test
    fun badge_completedOnPriorDate_isAllDone() {
        val priorDate = badge(completedOnPriorDate = LocalDate.of(2026, 3, 1))
        assertEquals(1f, priorDate.fractionDone(version))
    }

    private companion object {
        const val BADGE = "personal-fitness"
    }
}
