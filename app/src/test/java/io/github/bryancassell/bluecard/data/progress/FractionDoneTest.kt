package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.catalog.ColumnTotal
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.MeritBadgesNeeded
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
    fun fixedRowsAndOwnWork_countTheOwnWorkAsOneMorePart() {
        val ownWorkAndWeeks = weeks.copy(ownWork = "Sum up the weeks.")
        val allRows = entriesOf(row("5", 1), row("5", 2), row("5", 3), row("5", 4))
        assertEquals(0.2f, ownWorkAndWeeks.fractionDone(progressOf(done("5")), emptyMap()))
        val twoRows = entriesOf(row("5", 1), row("5", 3))
        assertEquals(0.6f, ownWorkAndWeeks.fractionDone(progressOf(done("5")), twoRows))
        assertEquals(0.8f, ownWorkAndWeeks.fractionDone(emptyMap(), allRows))
        assertEquals(1f, ownWorkAndWeeks.fractionDone(progressOf(done("5")), allRows))
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

    // Requirement 6 again, asking for at least three sessions.
    private val logOfThree = log.copy(tracker = log.tracker!!.copy(rowsNeeded = 3))

    @Test
    fun logThatNeedsRows_countsEachRowItNeedsAndMarkingIt() {
        assertEquals(0f, logOfThree.fractionDone(emptyMap(), emptyMap()))
        val twoRows = entriesOf(row("6", null), row("6", null))
        assertEquals(0.5f, logOfThree.fractionDone(emptyMap(), twoRows))
        val threeRows = entriesOf(row("6", null), row("6", null), row("6", null))
        assertEquals(0.75f, logOfThree.fractionDone(emptyMap(), threeRows))
        assertEquals(1f, logOfThree.fractionDone(progressOf(done("6")), twoRows))
    }

    @Test
    fun logThatNeedsRows_rowsPastTheNumberAddNothing() {
        val fiveRows = entriesOf(*Array(5) { row("6", null) })
        assertEquals(0.75f, logOfThree.fractionDone(emptyMap(), fiveRows))
    }

    // Requirement 6 again, asking for 6 hours of service, then as Life 4 asks for them: with at
    // least 3 of them on conservation, which its hours include.
    private fun hoursColumn(id: String, needed: Int, partOf: String? = null) = TrackerColumn(
        id,
        "Hours",
        TrackerColumnType.NUMBER,
        ColumnTotal(needed, "hour", "hours", partOf)
    )
    private val sixHours = log.copy(
        tracker = TrackerDefinition(listOf(hoursColumn("hours", 6)), "project", "projects")
    )
    private val sixHoursThreeOnConservation = log.copy(
        tracker = TrackerDefinition(
            listOf(hoursColumn("hours", 6), hoursColumn("conservation", 3, partOf = "hours")),
            "project",
            "projects"
        )
    )

    private fun project(hours: String, conservation: String = "0") = TrackerEntry(
        0,
        BADGE,
        "6",
        null,
        mapOf("hours" to hours, "conservation" to conservation),
        LocalDate.of(2026, 5, 1)
    )

    @Test
    fun logWithATotal_countsEachUnitItNeedsAndMarkingIt() {
        assertEquals(0f, sixHours.fractionDone(emptyMap(), emptyMap()))
        val fourAndAHalf = entriesOf(project("3"), project("1.5"))
        assertEquals(4.5f / 7, sixHours.fractionDone(emptyMap(), fourAndAHalf))
        assertEquals(6f / 7, sixHours.fractionDone(emptyMap(), entriesOf(project("6"))))
        assertEquals(1f, sixHours.fractionDone(progressOf(done("6")), fourAndAHalf))
    }

    @Test
    fun logWithATotal_unitsPastTheAmountAddNothing() {
        val nine = entriesOf(project("4"), project("5"))
        assertEquals(6f / 7, sixHours.fractionDone(emptyMap(), nine))
    }

    @Test
    fun logWithAPartOfATotal_countsTheUnitsThatGoTowardTheAmount() {
        fun fractionDone(vararg projects: TrackerEntry) =
            sixHoursThreeOnConservation.fractionDone(emptyMap(), entriesOf(*projects))

        // 3 conservation hours still to do.
        assertEquals(3f / 7, fractionDone(project("6")))
        // 3 more hours of any kind still to do.
        assertEquals(3f / 7, fractionDone(project("3", conservation = "3")))
        // 1.5 conservation hours still to do.
        assertEquals(4.5f / 7, fractionDone(project("3", conservation = "1.5"), project("1.5")))
        assertEquals(6f / 7, fractionDone(project("3"), project("3", conservation = "3")))
        // Conservation hours past the 3 count toward the rest.
        assertEquals(6f / 7, fractionDone(project("6", conservation = "6")))
    }

    @Test
    fun logWithAPartOfATotal_countsTheSameWithThePartFirst() {
        val partFirst = sixHoursThreeOnConservation.tracker!!.let {
            sixHoursThreeOnConservation.copy(tracker = it.copy(columns = it.columns.reversed()))
        }
        assertEquals(3f / 7, partFirst.fractionDone(emptyMap(), entriesOf(project("6"))))
    }

    @Test
    fun hasEnoughLogged_onceTheRowsReachTheNumber() {
        assertFalse(logOfThree.hasEnoughLogged(entriesOf(row("6", null), row("6", null))))
        assertTrue(logOfThree.hasEnoughLogged(entriesOf(*Array(3) { row("6", null) })))
        assertTrue(logOfThree.hasEnoughLogged(entriesOf(*Array(5) { row("6", null) })))
        // Another requirement's rows don't count.
        assertFalse(logOfThree.hasEnoughLogged(entriesOf(*Array(3) { row("7", null) })))
    }

    @Test
    fun hasEnoughLogged_onceTheTotalReachesTheAmount() {
        assertFalse(sixHours.hasEnoughLogged(entriesOf(project("3"), project("2.99"))))
        assertTrue(sixHours.hasEnoughLogged(entriesOf(project("3"), project("3"))))
        assertTrue(sixHours.hasEnoughLogged(entriesOf(project("4"), project("5"))))
    }

    // As Life 4's row reads "6 of 6 hours" above "2 of 3 conservation hours" until both are.
    @Test
    fun hasEnoughLogged_withAPartOfATotal_onceBothAreReached() {
        fun hasEnoughLogged(vararg projects: TrackerEntry) =
            sixHoursThreeOnConservation.hasEnoughLogged(entriesOf(*projects))

        assertFalse(hasEnoughLogged(project("6", conservation = "2")))
        assertFalse(hasEnoughLogged(project("3", conservation = "3")))
        assertTrue(hasEnoughLogged(project("6", conservation = "3")))
    }

    @Test
    fun hasEnoughLogged_isFalseWithoutANumberOrAmount() {
        assertFalse(log.hasEnoughLogged(entriesOf(*Array(5) { row("6", null) })))
        // A fixed-row tracker's rows complete its requirement instead.
        val allRows = entriesOf(row("5", 1), row("5", 2), row("5", 3), row("5", 4))
        assertFalse(weeks.hasEnoughLogged(allRows))
        assertFalse(leaf("1").hasEnoughLogged(emptyMap()))
    }

    @Test
    fun meritBadges_countTheBadgesNeededTheScoutHas() {
        // Six badges, four of them Eagle-required.
        val meritBadges = Requirement(
            number = "7",
            summary = "Earn six merit badges.",
            meritBadges = MeritBadgesNeeded(total = 6, eagleRequired = 4)
        )

        assertEquals(0f, meritBadges.fractionDone(emptyMap(), emptyMap(), EarnedBadges.None))
        // Three that aren't Eagle-required: only two of them count, as four more Eagle-required
        // badges are needed.
        assertEquals(
            2f / 6,
            meritBadges.fractionDone(emptyMap(), emptyMap(), earned(false, false, false))
        )
        assertEquals(
            1f,
            meritBadges.fractionDone(
                emptyMap(),
                emptyMap(),
                earned(false, false, true, true, true, true)
            )
        )
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

    // Requirement 7 has a sub-requirement with sub-requirements of its own, and requirement 8 has
    // both children and a fixed-row tracker.
    private val nestedTwice = Requirement(
        number = "7",
        summary = "Do both.",
        children = listOf(
            allOf.copy(number = "7a", children = listOf(leaf("7a(1)"), leaf("7a(2)"))),
            leaf("7b")
        )
    )
    private val childrenAndTracker = allOf.copy(
        number = "8",
        children = listOf(leaf("8a"), leaf("8b")),
        tracker = weeks.tracker
    )

    @Test
    fun isOneExactlyWhenComplete() {
        val requirements = listOf(
            leaf("1"),
            allOf,
            twoOf,
            ownWorkAndTwoOf.copy(number = "4"),
            weeks,
            log,
            nestedTwice,
            childrenAndTracker,
            weeks.copy(number = "9", ownWork = "Sum up the weeks."),
            log.copy(number = "10", tracker = log.tracker!!.copy(rowsNeeded = 2)),
            log.copy(
                number = "11",
                tracker = TrackerDefinition(
                    listOf(income.single().copy(total = ColumnTotal(5, "dollar", "dollars"))),
                    "week",
                    "weeks"
                )
            ),
            log.copy(
                number = "12",
                tracker = TrackerDefinition(
                    listOf(
                        income.single().copy(total = ColumnTotal(5, "dollar", "dollars")),
                        TrackerColumn(
                            "tips",
                            "Tips (included in Income)",
                            TrackerColumnType.NUMBER,
                            ColumnTotal(2, "dollar", "dollars", partOf = "income")
                        )
                    ),
                    "week",
                    "weeks"
                )
            )
        )
        val partOfEach = listOf(row("5", 1), row("5", 2), row("6", null), row("10", null)) +
            (1..4).map { row("8", it) }
        val recorded = listOf(
            progressOf() to entriesOf(),
            // Part of each, and requirement 8's tracker full while its children aren't.
            progressOf(done("1"), done("2a"), done("3a"), done("7a(1)"), done("8a")) to
                partOfEach.groupBy { it.requirementNumber },
            // Enough children for requirement 4, but not its own work, requirement 9's rows but
            // not its own work, and more rows than requirement 10 needs and more income and tips
            // than 11 and 12 need, but none of them marked.
            progressOf(done("3a"), done("3b"), done("7a(1)"), done("7a(2)"), done("7b")) to
                (
                    (1..4).map { row("9", it) } + List(3) { row("10", null) } + row("11", null) +
                        row("12", null).copy(values = mapOf("income" to "10", "tips" to "10"))
                    ).groupBy { it.requirementNumber },
            // Everything.
            progressOf(
                done("1"), done("2a"), done("2b"), done("3b"), done("3c"), done("4"), done("6"),
                done("7a(1)"), done("7a(2)"), done("7b"), done("8a"), done("8b"), done("9"),
                done("10"), done("11"), done("12")
            ) to (1..4).flatMap { listOf(row("5", it), row("9", it)) }
                .groupBy { it.requirementNumber }
        )
        for (requirement in requirements) {
            for ((progress, entries) in recorded) {
                val complete = requirement.completion(progress, entries) != null
                val fraction = requirement.fractionDone(progress, entries)
                val case = "${requirement.number} with ${progress.keys} and ${entries.keys}"
                assertEquals(case, complete, fraction == 1f)
                assertTrue(case, fraction in 0f..1f)
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

    private val personalFitness = MeritBadge(
        id = BADGE,
        name = "Personal Fitness",
        summary = "Our summary of Personal Fitness.",
        officialUrl = "https://www.scouting.org/merit-badges/personal-fitness/",
        requirementVersions = listOf(version)
    )

    @Test
    fun whileInProgress_notStarted_isNull() {
        assertNull(personalFitness.fractionDoneWhileInProgress(progress = null))
    }

    @Test
    fun whileInProgress_isHowMuchIsDone_includingNothing() {
        assertEquals(0f, personalFitness.fractionDoneWhileInProgress(badge()))
        // Half of requirement 2, out of four top-level requirements.
        assertEquals(0.125f, personalFitness.fractionDoneWhileInProgress(badge(done("2a"))))
    }

    @Test
    fun whileInProgress_complete_isNull() {
        val all = badge(
            done("1"),
            done("2a"),
            done("2b"),
            done("3a"),
            done("3c"),
            trackerEntries = (1..4).map { row("5", it) }
        )
        assertNull(personalFitness.fractionDoneWhileInProgress(all))
    }

    @Test
    fun whileInProgress_completedOnPriorDate_isNull() {
        val priorDate = badge(completedOnPriorDate = LocalDate.of(2026, 3, 1))
        assertNull(personalFitness.fractionDoneWhileInProgress(priorDate))
    }

    @Test
    fun whileInProgress_onAVersionMissingFromTheCatalog_isNull() {
        // The badge is in progress, but there are no requirements to measure it against.
        val missing = LocalDate.of(2020, 1, 1)
        val onMissingVersion = BadgeProgressDetails(
            BadgeProgress(BADGE, missing, missing),
            listOf(done("1")),
            trackerEntries = emptyList()
        )
        assertEquals(BadgeStatus.InProgress, personalFitness.status(onMissingVersion))
        assertNull(personalFitness.fractionDoneWhileInProgress(onMissingVersion))
    }

    /** Badges completed with no date, each Eagle-required or not. */
    private fun earned(vararg eagleRequired: Boolean) = EarnedBadges(
        eagleRequired.mapIndexed { index, counts ->
            EarnedBadge(
                personalFitness.copy(id = "badge-$index", eagleRequired = counts),
                null,
                counts
            )
        }
    )

    private companion object {
        const val BADGE = "personal-fitness"
    }
}
