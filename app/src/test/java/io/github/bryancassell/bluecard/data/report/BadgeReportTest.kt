package io.github.bryancassell.bluecard.data.report

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.progress.BadgeProgress
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.Completion
import io.github.bryancassell.bluecard.data.progress.Counselor
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import io.github.bryancassell.bluecard.data.progress.TrackerEntry
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BadgeReportTest {
    private val older = LocalDate.of(2025, 1, 1)
    private val newest = LocalDate.of(2026, 1, 1)
    private val started = LocalDate.of(2026, 3, 1)
    private val today = LocalDate.of(2026, 9, 30)
    private val profile = Profile("Alex Scout", "123")

    private val date = TrackerColumn("date", "Date", TrackerColumnType.DATE)
    private val nights = TrackerColumn("nights", "Nights", TrackerColumnType.NUMBER)
    private val place = TrackerColumn("place", "Place", TrackerColumnType.TEXT)
    private val log = TrackerDefinition(listOf(date, nights, place), "trip", "trips")
    private val weeks = TrackerDefinition(listOf(date, place), "week", "weeks", rowCount = 3)

    private val camping = MeritBadge(
        id = "camping",
        name = "Camping",
        summary = "Our summary of Camping.",
        officialUrl = "https://www.scouting.org/merit-badges/camping/",
        requirementVersions = listOf(
            RequirementsVersion(older, listOf(Requirement("1", "An older requirement."))),
            RequirementsVersion(
                newest,
                listOf(
                    Requirement("1", "Plan a campout."),
                    Requirement(
                        "2",
                        "Do two of these.",
                        requiredCount = 2,
                        children = listOf(
                            Requirement("2a", "Cook a meal."),
                            Requirement("2b", "Lead a hike."),
                            Requirement("2c", "Pitch a tent.")
                        )
                    ),
                    Requirement("3", "Keep a camping log.", tracker = log),
                    Requirement(
                        "4",
                        "Do both of these.",
                        // Two of two is all of them, not a choice.
                        requiredCount = 2,
                        children = listOf(
                            Requirement("4a", "Camp three weeks.", tracker = weeks),
                            Requirement("4b", "Treat a blister.")
                        )
                    )
                )
            )
        )
    )

    private fun progress(
        requirements: List<RequirementProgress> = emptyList(),
        trackerEntries: List<TrackerEntry> = emptyList(),
        version: LocalDate = newest,
        counselor: Counselor? = null,
        completedOnPriorDate: LocalDate? = null
    ) = BadgeProgressDetails(
        BadgeProgress("camping", version, started, counselor, completedOnPriorDate),
        requirements,
        trackerEntries
    )

    private fun completed(number: String, date: LocalDate?, comment: String? = null) =
        RequirementProgress("camping", number, completed = true, completedDate = date, comment)

    private fun report(progress: BadgeProgressDetails) = camping.report(profile, progress, today)!!

    private fun BadgeReport.requirement(number: String): ReportRequirement {
        fun List<ReportRequirement>.find(): ReportRequirement? =
            firstNotNullOfOrNull { if (it.number == number) it else it.children.find() }
        return requirements.find()!!
    }

    @Test
    fun report_hasTheScoutBadgeVersionCounselorAndDay() {
        val counselor = Counselor("Pat Lee", "555-0100", "pat@example.com")

        val report = report(progress(counselor = counselor))

        assertEquals(profile, report.profile)
        assertEquals("Camping", report.badgeName)
        assertEquals(newest, report.requirementsVersion)
        assertEquals(counselor, report.counselor)
        assertEquals(today, report.createdDate)
    }

    @Test
    fun report_hasEveryRequirementOfItsVersion_inOrder() {
        val report = report(progress())

        assertEquals(listOf("1", "2", "3", "4"), report.requirements.map { it.number })
        assertEquals(
            listOf("2a", "2b", "2c"),
            report.requirement("2").children.map { it.number }
        )
        assertEquals("Cook a meal.", report.requirement("2a").summary)
    }

    @Test
    fun badgeStartedOnOlderVersion_reportsThatVersion() {
        val report = report(progress(version = older))

        assertEquals(older, report.requirementsVersion)
        assertEquals(listOf("An older requirement."), report.requirements.map { it.summary })
    }

    @Test
    fun badgeOnVersionMissingFromCatalog_hasNoReport() {
        val progress = progress(version = LocalDate.of(2020, 1, 1))

        assertNull(camping.report(profile, progress, today))
    }

    @Test
    fun choice_saysHowManyAreNeeded_onlyWhenFewerThanAll() {
        val report = report(progress())

        assertEquals(2, report.requirement("2").requiredCount)
        assertNull(report.requirement("4").requiredCount)
        assertNull(report.requirement("1").requiredCount)
    }

    @Test
    fun requirements_haveTheirCompletionDatesAndComments() {
        val report = report(
            progress(
                listOf(
                    completed("1", LocalDate.of(2026, 4, 1), "Planned with my patrol."),
                    completed("2a", LocalDate.of(2026, 4, 2)),
                    completed("2c", null),
                    RequirementProgress("camping", "2b", comment = "Next month.")
                )
            )
        )

        val one = report.requirement("1")
        assertEquals(Completion(LocalDate.of(2026, 4, 1)), one.completion)
        assertEquals("Planned with my patrol.", one.comment)
        // Completed, without a date.
        assertEquals(Completion(null), report.requirement("2c").completion)
        // Not completed, with a comment.
        assertNull(report.requirement("2b").completion)
        assertEquals("Next month.", report.requirement("2b").comment)
        // Two of three are complete, but one has no date.
        assertEquals(Completion(null), report.requirement("2").completion)
        assertNull(report.requirement("3").completion)
    }

    @Test
    fun badge_isCompletedOnItsLastRequirementsDate() {
        val all = listOf("1", "2a", "2b", "3", "4b").mapIndexed { index, number ->
            completed(number, LocalDate.of(2026, 5, index + 1))
        }
        val rows = (1..3).map {
            TrackerEntry(
                id = it.toLong(),
                badgeId = "camping",
                requirementNumber = "4a",
                rowNumber = it,
                values = emptyMap(),
                addedDate = LocalDate.of(2026, 6, it)
            )
        }

        val report = report(progress(all, rows))

        assertEquals(Completion(LocalDate.of(2026, 6, 3)), report.completion)
    }

    @Test
    fun badgeMarkedCompletedOnPriorDate_isCompletedThen() {
        val report = report(progress(completedOnPriorDate = LocalDate.of(2025, 8, 1)))

        assertEquals(Completion(LocalDate.of(2025, 8, 1)), report.completion)
    }

    @Test
    fun incompleteBadge_isNotCompleted() {
        assertNull(report(progress()).completion)
    }

    @Test
    fun log_listsItsRowsInTheOrderAdded_withValuesInColumnOrder() {
        fun trip(id: Long, values: Map<String, String>) = TrackerEntry(
            id = id,
            badgeId = "camping",
            requirementNumber = "3",
            values = values
        )
        val report = report(
            progress(
                trackerEntries = listOf(
                    trip(7, mapOf("place" to "Lake Sebago", "date" to "2026-05-02")),
                    trip(3, mapOf("nights" to "2", "place" to "Bear Mountain"))
                )
            )
        )

        val tracker = report.requirement("3").tracker!!
        assertEquals(log, tracker.definition)
        assertEquals(
            listOf(
                ReportTrackerRow(1, listOf(nights to "2", place to "Bear Mountain")),
                ReportTrackerRow(2, listOf(date to "2026-05-02", place to "Lake Sebago"))
            ),
            tracker.rows
        )
    }

    @Test
    fun fixedRowTracker_listsTheRowsFilledIn_byRowNumber() {
        fun week(row: Int, place: String) = TrackerEntry(
            id = 10L - row,
            badgeId = "camping",
            requirementNumber = "4a",
            rowNumber = row,
            values = mapOf("place" to place)
        )
        val entries = listOf(week(3, "Camp Rotary"), week(1, "Home"))

        val report = report(progress(trackerEntries = entries))

        assertEquals(
            listOf(
                ReportTrackerRow(1, listOf(place to "Home")),
                ReportTrackerRow(3, listOf(place to "Camp Rotary"))
            ),
            report.requirement("4a").tracker!!.rows
        )
    }

    @Test
    fun requirementWithoutTracker_hasNone() {
        assertNull(report(progress()).requirement("1").tracker)
    }
}
