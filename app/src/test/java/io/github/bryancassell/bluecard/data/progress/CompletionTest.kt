package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionTest {
    private fun day(n: Int) = LocalDate.of(2026, 5, n)

    private fun leaf(number: String) = Requirement(number = number, summary = "Do $number.")

    private fun done(number: String, date: LocalDate? = null) =
        RequirementProgress(BADGE, number, completed = true, completedDate = date)

    private fun progressOf(vararg progress: RequirementProgress) =
        progress.associateBy { it.requirementNumber }

    /** Completion from requirement progress alone, with no tracker entries. */
    private fun Requirement.completion(progress: Map<String, RequirementProgress>) =
        completion(progress, emptyMap())

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
    fun leaf_completeOnlyWhenMarked() {
        assertNull(leaf("1").completion(progressOf()))
        assertNull(
            leaf("1").completion(progressOf(RequirementProgress(BADGE, "1", comment = "Started")))
        )
        assertEquals(Completion(day(3)), leaf("1").completion(progressOf(done("1", day(3)))))
        assertEquals(Completion(null), leaf("1").completion(progressOf(done("1"))))
    }

    @Test
    fun allChildrenRequired_completeWhenAllDone_onLastDate() {
        assertNull(allOf.completion(progressOf(done("2a", day(1)))))
        assertEquals(
            Completion(day(9)),
            allOf.completion(progressOf(done("2a", day(9)), done("2b", day(4))))
        )
    }

    @Test
    fun allChildrenRequired_anUndatedChildMeansNoDate() {
        assertEquals(Completion(null), allOf.completion(progressOf(done("2a", day(9)), done("2b"))))
    }

    @Test
    fun choice_completeWhenEnoughChildrenDone() {
        assertNull(twoOf.completion(progressOf(done("3a", day(1)))))
        assertEquals(
            Completion(day(5)),
            twoOf.completion(progressOf(done("3a", day(1)), done("3c", day(5))))
        )
    }

    @Test
    fun choice_withExtraChildrenDone_datedWhenEnoughWereDone() {
        // Complete once the second of the three was done, on the 5th.
        val progress = progressOf(done("3a", day(9)), done("3b", day(1)), done("3c", day(5)))
        assertEquals(Completion(day(5)), twoOf.completion(progress))
    }

    @Test
    fun choice_undatedChildrenOnlyMatterIfTheyAreNeeded() {
        assertEquals(
            Completion(day(5)),
            twoOf.completion(progressOf(done("3a", day(1)), done("3b"), done("3c", day(5))))
        )
        assertEquals(
            Completion(null),
            twoOf.completion(progressOf(done("3a", day(1)), done("3b")))
        )
    }

    // Like Photography 4: do two of 3a-3c, then something of its own (share the photos).
    private val ownWorkAndTwoOf = twoOf.copy(ownWork = "Explain it.")

    @Test
    fun ownWork_incompleteUntilBothItAndEnoughChildrenAreDone() {
        val children = arrayOf(done("3a", day(1)), done("3c", day(5)))
        assertNull(ownWorkAndTwoOf.completion(progressOf(*children)))
        assertNull(ownWorkAndTwoOf.completion(progressOf(done("3", day(2)), done("3a", day(1)))))
        assertEquals(
            Completion(day(5)),
            ownWorkAndTwoOf.completion(progressOf(done("3", day(2)), *children))
        )
    }

    @Test
    fun ownWork_doneLast_datesTheRequirement() {
        val progress = progressOf(done("3", day(9)), done("3a", day(1)), done("3c", day(5)))
        assertEquals(Completion(day(9)), ownWorkAndTwoOf.completion(progress))
    }

    @Test
    fun ownWork_anUndatedPartMeansNoDate() {
        assertEquals(
            Completion(null),
            ownWorkAndTwoOf.completion(
                progressOf(done("3"), done("3a", day(1)), done("3c", day(5)))
            )
        )
        assertEquals(
            Completion(null),
            ownWorkAndTwoOf.completion(
                progressOf(done("3", day(2)), done("3a", day(1)), done("3c"))
            )
        )
    }

    @Test
    fun hasEnoughChildren_onceEnoughAreDone_evenWithoutItsOwnWork() {
        assertFalse(ownWorkAndTwoOf.hasEnoughChildren(progressOf(done("3a")), emptyMap()))
        assertTrue(
            ownWorkAndTwoOf.hasEnoughChildren(progressOf(done("3a"), done("3c")), emptyMap())
        )
        assertTrue(twoOf.hasEnoughChildren(progressOf(done("3a"), done("3c")), emptyMap()))
        assertFalse(leaf("1").hasEnoughChildren(progressOf(done("1")), emptyMap()))
    }

    @Test
    fun nestedChoice_countsCompleteSubtrees() {
        // "Do two of 4a-4c", where 4c is itself "do two of 4c(1)-4c(3)".
        val nested = Requirement(
            number = "4",
            summary = "Do two.",
            requiredCount = 2,
            children = listOf(
                leaf("4a"),
                leaf("4b"),
                Requirement(
                    number = "4c",
                    summary = "Do two.",
                    requiredCount = 2,
                    children = listOf(leaf("4c(1)"), leaf("4c(2)"), leaf("4c(3)"))
                )
            )
        )
        assertNull(nested.completion(progressOf(done("4a", day(1)), done("4c(1)", day(2)))))
        assertEquals(
            Completion(day(7)),
            nested.completion(
                progressOf(done("4a", day(1)), done("4c(1)", day(2)), done("4c(3)", day(7)))
            )
        )
    }

    // Requirement 5 is a tracker with three rows, and requirement 6 a log.
    private val income = listOf(TrackerColumn("income", "Income", TrackerColumnType.NUMBER))
    private val weeks = Requirement(
        number = "5",
        summary = "Track three weeks.",
        tracker = TrackerDefinition(income, "week", "weeks", rowCount = 3)
    )
    private val log = Requirement(
        number = "6",
        summary = "Keep a log.",
        tracker = TrackerDefinition(income, "session", "sessions")
    )

    private fun row(number: String, rowNumber: Int?, addedDate: LocalDate? = null) =
        TrackerEntry(0, BADGE, number, rowNumber, mapOf("income" to "10"), addedDate)

    private fun entriesOf(vararg entries: TrackerEntry) = entries.groupBy { it.requirementNumber }

    @Test
    fun isMarkedByHand_onlyWithoutChildrenOrFixedRows() {
        assertTrue(leaf("1").isMarkedByHand)
        assertTrue(log.isMarkedByHand)
        assertFalse(allOf.isMarkedByHand)
        assertFalse(ownWorkAndTwoOf.isMarkedByHand)
        assertFalse(weeks.isMarkedByHand)
    }

    @Test
    fun fixedRows_completeOnlyWhenEveryRowIsFilled() {
        val twoRows = entriesOf(row("5", 1, day(1)), row("5", 3, day(2)))
        assertNull(weeks.completion(progressOf(), twoRows))

        val allRows = entriesOf(row("5", 1, day(1)), row("5", 2, day(4)), row("5", 3, day(2)))
        assertEquals(Completion(day(4)), weeks.completion(progressOf(), allRows))
    }

    @Test
    fun isCompleteFromRows_onlyWithoutChildrenAndWithFixedRows() {
        assertTrue(weeks.completesFromRows)
        assertFalse(leaf("1").completesFromRows)
        assertFalse(log.completesFromRows)
        assertFalse(allOf.copy(tracker = weeks.tracker).completesFromRows)
    }

    @Test
    fun fixedRows_aStoredMarkDoesNotCompleteThem() {
        assertNull(weeks.completion(progressOf(done("5", day(3))), entriesOf()))
        val twoRows = entriesOf(row("5", 1, day(1)), row("5", 3, day(2)))
        assertNull(weeks.completion(progressOf(done("5", day(3))), twoRows))
    }

    @Test
    fun fixedRows_onceFilledIn_areCompleteOnTheDateTheScoutGave() {
        val allRows = entriesOf(row("5", 1, day(1)), row("5", 2, day(4)), row("5", 3, day(2)))
        // Earlier or later than the rows' dates.
        assertEquals(Completion(day(3)), weeks.completion(progressOf(done("5", day(3))), allRows))
        assertEquals(Completion(day(9)), weeks.completion(progressOf(done("5", day(9))), allRows))
        // The scout removed the date.
        assertEquals(Completion(null), weeks.completion(progressOf(done("5")), allRows))
        // A comment alone gives no date.
        val comment = RequirementProgress(BADGE, "5", comment = "Weekly.")
        assertEquals(Completion(day(4)), weeks.completion(progressOf(comment), allRows))
    }

    @Test
    fun rowsCompletedDate_isTheLatestRowsDate_onceEveryRowIsFilledIn() {
        assertNull(weeks.rowsCompletedDate(entriesOf(row("5", 1, day(1)), row("5", 3, day(2)))))

        val allRows = entriesOf(row("5", 1, day(1)), row("5", 2, day(4)), row("5", 3, day(2)))
        assertEquals(day(4), weeks.rowsCompletedDate(allRows))

        val noDate = entriesOf(row("5", 1, day(1)), row("5", 2), row("5", 3, day(2)))
        assertNull(weeks.rowsCompletedDate(noDate))
    }

    @Test
    fun rowsCompletedDate_isNull_forOtherRequirements() {
        assertNull(log.rowsCompletedDate(entriesOf(row("6", null, day(1)))))
        // Its children complete it, even with every row filled in.
        val parent = allOf.copy(tracker = weeks.tracker)
        val allRows = entriesOf(row("2", 1, day(1)), row("2", 2, day(1)), row("2", 3, day(1)))
        assertNull(parent.rowsCompletedDate(allRows))
    }

    @Test
    fun fixedRows_aRowWithoutADateMeansNoDate() {
        val allRows = entriesOf(row("5", 1, day(1)), row("5", 2), row("5", 3, day(2)))
        assertEquals(Completion(null), weeks.completion(progressOf(), allRows))
    }

    @Test
    fun fixedRows_countOnlyTheirOwnRows() {
        // Another requirement's rows, and a row outside the tracker, fill none of them.
        val entries = entriesOf(
            row("5", 1, day(1)),
            row("5", 2, day(1)),
            row("5", 4, day(1)),
            row("7", 3, day(1))
        )
        assertNull(weeks.completion(progressOf(), entries))
    }

    @Test
    fun log_completeOnlyWhenMarked() {
        val entries = entriesOf(row("6", null, day(1)), row("6", null, day(2)))
        assertNull(log.completion(progressOf(), entries))
        assertEquals(Completion(day(3)), log.completion(progressOf(done("6", day(3))), entries))
    }

    @Test
    fun children_withFixedRows_countTheirRows() {
        val parent = Requirement(
            number = "8",
            summary = "Do both.",
            children = listOf(leaf("8a"), weeks.copy(number = "8b"))
        )
        val rows = entriesOf(row("8b", 1, day(5)), row("8b", 2, day(6)), row("8b", 3, day(7)))
        assertNull(parent.completion(progressOf(done("8a", day(2))), entriesOf()))
        assertEquals(Completion(day(7)), parent.completion(progressOf(done("8a", day(2))), rows))
    }

    private val version = RequirementsVersion(day(1), listOf(leaf("1"), allOf, twoOf))

    private fun badge(
        vararg progress: RequirementProgress,
        completedOnPriorDate: LocalDate? = null,
        trackerEntries: List<TrackerEntry> = emptyList()
    ) = BadgeProgressDetails(
        BadgeProgress(BADGE, day(1), day(1), completedOnPriorDate = completedOnPriorDate),
        progress.toList(),
        trackerEntries
    )

    @Test
    fun hasPartDone_onceARequirementUnderItAtAnyDepthIsMarkedComplete() {
        val parent = Requirement(number = "9", summary = "Do these.", children = listOf(allOf))

        assertFalse(parent.hasPartDone(progressOf(), entriesOf()))
        assertTrue(parent.hasPartDone(progressOf(done("2a")), entriesOf()))
    }

    @Test
    fun hasPartDone_onceItsOwnWorkIsMarkedComplete() {
        assertTrue(ownWorkAndTwoOf.hasPartDone(progressOf(done("3")), entriesOf()))
    }

    // Left from an older catalog, a mark on a requirement the scout can't mark by hand isn't
    // progress, as it isn't for completion.
    @Test
    fun hasPartDone_ignoresAStoredMarkThatDoesntCount() {
        assertFalse(allOf.hasPartDone(progressOf(done("2")), entriesOf()))
        assertFalse(weeks.hasPartDone(progressOf(done("5")), entriesOf()))
    }

    @Test
    fun hasPartDone_onceATrackerRowIsFilledIn_onItOrUnderIt() {
        assertTrue(weeks.hasPartDone(progressOf(), entriesOf(row("5", 2))))
        assertTrue(log.hasPartDone(progressOf(), entriesOf(row("6", null))))

        val parent = Requirement(
            number = "8",
            summary = "Do both.",
            children = listOf(leaf("8a"), weeks.copy(number = "8b"))
        )
        assertTrue(parent.hasPartDone(progressOf(), entriesOf(row("8b", 1))))
    }

    @Test
    fun hasPartDone_countsOnlyATrackersOwnRows() {
        // A row outside the tracker, and another requirement's row, fill in none of its rows.
        assertFalse(weeks.hasPartDone(progressOf(), entriesOf(row("5", 4), row("7", 1))))
    }

    @Test
    fun badge_incompleteUntilEveryTopLevelRequirementIs() {
        assertNull(badge().completion(version))
        assertNull(
            badge(done("1", day(2)), done("2a", day(3)), done("2b", day(4))).completion(version)
        )
    }

    @Test
    fun badge_completeOnLastRequirementDate() {
        val all = badge(
            done("1", day(2)),
            done("2a", day(3)),
            done("2b", day(8)),
            done("3a", day(4)),
            done("3b", day(6))
        )
        assertEquals(Completion(day(8)), all.completion(version))
    }

    @Test
    fun badge_completeWithoutDateWhenANeededDateIsMissing() {
        val all = badge(done("1"), done("2a", day(3)), done("2b", day(8)), done("3a"), done("3b"))
        assertEquals(Completion(null), all.completion(version))
    }

    @Test
    fun badge_countsAFilledTracker() {
        val withTracker = RequirementsVersion(day(1), listOf(leaf("1"), weeks))
        val twoRows = listOf(row("5", 1, day(3)), row("5", 2, day(10)))

        assertNull(badge(done("1", day(2)), trackerEntries = twoRows).completion(withTracker))
        assertEquals(
            Completion(day(11)),
            badge(done("1", day(2)), trackerEntries = twoRows + row("5", 3, day(11)))
                .completion(withTracker)
        )
    }

    @Test
    fun badge_completedOnPriorDate_isCompleteOnThatDate() {
        assertEquals(Completion(day(20)), badge(completedOnPriorDate = day(20)).completion(version))
        // The prior date wins even when requirement progress is also complete.
        assertEquals(
            Completion(day(20)),
            badge(done("1", day(2)), completedOnPriorDate = day(20)).completion(
                RequirementsVersion(day(1), listOf(leaf("1")))
            )
        )
    }

    private companion object {
        const val BADGE = "personal-fitness"
    }
}
