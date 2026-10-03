package io.github.bryancassell.bluecard.data.report

import io.github.bryancassell.bluecard.data.catalog.ColumnTotal
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.MeritBadgesNeeded
import io.github.bryancassell.bluecard.data.catalog.Rank
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
import io.github.bryancassell.bluecard.data.progress.EarnedBadge
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import io.github.bryancassell.bluecard.data.progress.TrackerEntry
import io.github.bryancassell.bluecard.data.progress.TrackerTotal
import io.github.bryancassell.bluecard.data.progress.earnedBadges
import io.github.bryancassell.bluecard.data.progress.standings
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancementReportTest {
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

    private fun AdvancementReport.requirement(number: String): ReportRequirement {
        fun List<ReportRequirement>.find(): ReportRequirement? =
            firstNotNullOfOrNull { if (it.number == number) it else it.children.find() }
        return requirements.find()!!
    }

    @Test
    fun report_hasTheScoutBadgeVersionCounselorAndDay() {
        val counselor = Counselor("Pat Lee", "555-0100", "pat@example.com")

        val report = report(progress(counselor = counselor))

        assertEquals(profile, report.profile)
        assertEquals(ReportKind.MeritBadge, report.kind)
        assertEquals("Camping", report.name)
        assertEquals(newest, report.requirementsVersion)
        assertEquals(counselor, report.counselor)
        assertEquals(today, report.createdDate)
        assertNull(report.earnedWith)
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

    // As the screens show it, so the counselor sees a choice the scout didn't pick isn't missing.
    @Test
    fun requirementNotCompleted_inACompletedOne_isNotNeeded() {
        val report = report(
            progress(listOf(completed("2a", LocalDate.of(2026, 4, 2)), completed("2c", null)))
        )

        assertTrue(report.requirement("2b").notNeeded)
        assertFalse(report.requirement("2a").notNeeded)
        assertFalse(report.requirement("2").notNeeded)
        // Requirement 4 isn't complete.
        assertFalse(report.requirement("4b").notNeeded)
    }

    @Test
    fun requirementNotCompleted_inOneWithEnoughDoneButNotItsOwnWork_isNotNeeded() {
        val withOwnWork = camping.copy(
            requirementVersions = camping.requirementVersions.map { version ->
                version.copy(
                    requirements = version.requirements.map {
                        if (it.number == "2") it.copy(ownWork = "Share what you did.") else it
                    }
                )
            }
        )
        val report = withOwnWork.report(
            profile,
            progress(listOf(completed("2a", LocalDate.of(2026, 4, 2)), completed("2c", null))),
            today
        )!!

        assertNull(report.requirement("2").completion)
        assertTrue(report.requirement("2b").notNeeded)
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

    // As the screens show it, so the counselor doesn't read the badge as complete with nothing
    // done.
    @Test
    fun requirementStillNeeded_onABadgeMarkedCompletedOnPriorDate_isNotRecorded() {
        // 2 is complete, so 2b isn't needed.
        val done = listOf(completed("1", null), completed("2a", null), completed("2c", null))
        val marked = LocalDate.of(2025, 8, 1)

        val report = report(progress(done, completedOnPriorDate = marked))

        assertEquals(listOf("3", "4", "4a", "4b"), report.notRecorded())
        // Not on a badge that isn't marked.
        assertEquals(emptyList<String>(), report(progress(done)).notRecorded())
    }

    // Its recorded parts are listed under it, so "Not recorded" would contradict them.
    @Test
    fun requirementPartlyDone_onABadgeMarkedCompletedOnPriorDate_isNotNotRecorded() {
        val trip = TrackerEntry(1, "camping", "3", values = mapOf("nights" to "2"))
        val progress = progress(
            listOf(completed("4b", null)),
            listOf(trip),
            completedOnPriorDate = LocalDate.of(2025, 8, 1)
        )

        assertEquals(listOf("1", "2", "2a", "2b", "2c", "4a"), report(progress).notRecorded())
    }

    /** The numbers of the requirements that are [ReportRequirement.notRecorded], at any depth. */
    private fun AdvancementReport.notRecorded(): List<String> {
        fun List<ReportRequirement>.all(): List<ReportRequirement> =
            flatMap { listOf(it) + it.children.all() }
        return requirements.all().filter { it.notRecorded }.map { it.number }
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
    fun log_addsUpItsColumnsWithATotal() {
        val twentyNights = ColumnTotal(20, "night", "nights")
        val withTotal = log.copy(columns = listOf(date, nights.copy(total = twentyNights), place))
        val badge = camping.copy(
            requirementVersions = listOf(
                RequirementsVersion(
                    newest,
                    listOf(Requirement("3", "Keep a camping log.", tracker = withTotal))
                )
            )
        )
        val entries = listOf(2L to "2", 3L to "1.5").map { (id, nights) ->
            TrackerEntry(
                id = id,
                badgeId = "camping",
                requirementNumber = "3",
                values = mapOf("nights" to nights)
            )
        }

        val report = badge.report(profile, progress(trackerEntries = entries), today)!!

        assertEquals(
            listOf(TrackerTotal(BigDecimal("3.5"), twentyNights)),
            report.requirement("3").tracker!!.totals
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

    // Ranks. Tenderfoot's requirement 2 asks for two merit badges, one of them Eagle-required.

    private val chess = MeritBadge(
        id = "chess",
        name = "Chess",
        summary = "Our summary of Chess.",
        officialUrl = "https://www.scouting.org/merit-badges/chess/",
        requirementVersions = listOf(
            RequirementsVersion(newest, listOf(Requirement("1", "Play a game.")))
        )
    )
    private val eagleCamping = camping.copy(eagleRequired = true)
    private val badges = listOf(eagleCamping, chess)

    private fun rank(id: String, name: String, requirements: List<Requirement>) = Rank(
        id = id,
        name = name,
        summary = "Our summary of $name.",
        officialUrl = "https://www.scouting.org/$id.pdf",
        requirementVersions = listOf(RequirementsVersion(newest, requirements))
    )

    private val ranks = listOf(
        rank("scout", "Scout", listOf(Requirement("1", "Learn the Scout Oath."))),
        rank(
            "tenderfoot",
            "Tenderfoot",
            listOf(
                Requirement("1", "Pitch a tent."),
                Requirement(
                    "2",
                    "Earn two merit badges.",
                    meritBadges = MeritBadgesNeeded(total = 2, eagleRequired = 1)
                )
            )
        ),
        rank("second-class", "Second Class", listOf(Requirement("1", "Cook a meal.")))
    )

    private fun rankProgress(
        id: String,
        requirements: List<RequirementProgress> = emptyList(),
        markedOn: LocalDate? = null
    ) = BadgeProgressDetails(
        BadgeProgress(id, newest, started, completedOnPriorDate = markedOn),
        requirements,
        emptyList()
    )

    private fun rankCompleted(
        id: String,
        number: String,
        date: LocalDate?,
        comment: String? = null
    ) = RequirementProgress(id, number, completed = true, completedDate = date, comment)

    /** A badge marked completed on [date], which counts toward a rank's merit badges. */
    private fun badgeCompleted(id: String, date: LocalDate) = BadgeProgressDetails(
        BadgeProgress(id, newest, started, completedOnPriorDate = date),
        emptyList(),
        emptyList()
    )

    /** The report on rank [id], from [progress] on badges and ranks. */
    private fun rankReport(id: String, vararg progress: BadgeProgressDetails): AdvancementReport? {
        val byId = progress.associateBy { it.badge.badgeId }
        val earnedBadges = badges.earnedBadges(byId)
        return ranks.standings(byId, earnedBadges)
            .first { it.rank.id == id }
            .report(profile, byId[id], earnedBadges, today)
    }

    @Test
    fun rankEarnedFromItsRequirements_isEarnedOnTheirLastDate_withNoCounselor() {
        val report = rankReport(
            "scout",
            rankProgress("scout", listOf(rankCompleted("scout", "1", LocalDate.of(2026, 4, 1))))
        )!!

        assertEquals(ReportKind.Rank, report.kind)
        assertEquals("Scout", report.name)
        assertEquals(profile, report.profile)
        assertEquals(newest, report.requirementsVersion)
        assertEquals(Completion(LocalDate.of(2026, 4, 1)), report.completion)
        assertNull(report.earnedWith)
        assertNull(report.counselor)
        assertEquals(today, report.createdDate)
    }

    @Test
    fun rankEarned_withARequirementWithoutADate_isEarnedWithNoDate() {
        val report = rankReport(
            "scout",
            rankProgress("scout", listOf(rankCompleted("scout", "1", null)))
        )!!

        assertEquals(Completion(null), report.completion)
    }

    // Its requirements are complete, but Scout isn't earned.
    @Test
    fun rankNotEarned_isNotCompleted() {
        val report = rankReport(
            "second-class",
            rankProgress("second-class", listOf(rankCompleted("second-class", "1", null)))
        )!!

        assertNull(report.completion)
        assertNull(report.earnedWith)
    }

    @Test
    fun rankMarkedEarned_isEarnedThen_andWhatsNotRecordedSaysSo() {
        val marked = LocalDate.of(2025, 8, 1)
        val report = rankReport(
            "tenderfoot",
            rankProgress("tenderfoot", markedOn = marked)
        )!!

        assertEquals(Completion(marked), report.completion)
        assertNull(report.earnedWith)
        assertEquals(listOf(true, true), report.requirements.map { it.notRecorded })
    }

    // As on its page, it reads as marked itself, on the version it would be started on.
    @Test
    fun rankCountedAsEarnedWithARankAbove_saysSo_andWhatsNotRecordedSaysSo() {
        val report = rankReport(
            "scout",
            rankProgress("tenderfoot", markedOn = LocalDate.of(2025, 8, 1))
        )!!

        assertNull(report.completion)
        assertEquals("Tenderfoot", report.earnedWith)
        assertEquals(newest, report.requirementsVersion)
        assertTrue(report.requirements.single().notRecorded)
    }

    @Test
    fun rankRequirements_haveWhatTheScoutRecorded() {
        val report = rankReport(
            "tenderfoot",
            rankProgress(
                "tenderfoot",
                listOf(rankCompleted("tenderfoot", "1", LocalDate.of(2026, 4, 1), "At camp."))
            )
        )!!

        val pitch = report.requirement("1")
        assertEquals(Completion(LocalDate.of(2026, 4, 1)), pitch.completion)
        assertEquals("At camp.", pitch.comment)
        assertNull(pitch.meritBadges)
    }

    @Test
    fun requirementThatAsksForMeritBadges_listsEveryBadgeCompleted_inNameOrder() {
        val report = rankReport(
            "tenderfoot",
            badgeCompleted("chess", LocalDate.of(2026, 5, 1)),
            badgeCompleted("camping", LocalDate.of(2026, 5, 3))
        )!!

        val badgesNeeded = report.requirement("2")
        assertEquals(Completion(LocalDate.of(2026, 5, 3)), badgesNeeded.completion)
        val meritBadges = badgesNeeded.meritBadges!!
        assertEquals(2, meritBadges.credit.completed)
        assertEquals(1, meritBadges.credit.eagleRequired)
        assertEquals(
            listOf("Camping" to LocalDate.of(2026, 5, 3), "Chess" to LocalDate.of(2026, 5, 1)),
            meritBadges.badges.map { it.badge.name to it.completedOn }
        )
    }

    @Test
    fun requirementThatAsksForMeritBadges_withNoneCompleted_listsNone() {
        val report = rankReport("tenderfoot")!!

        val badgesNeeded = report.requirement("2")
        assertNull(badgesNeeded.completion)
        assertEquals(0, badgesNeeded.meritBadges!!.credit.completed)
        assertEquals(emptyList<EarnedBadge>(), badgesNeeded.meritBadges.badges)
    }

    // A badge that counts toward it is a part done, so it isn't "Not recorded".
    @Test
    fun requirementThatAsksForMeritBadges_withABadge_onAMarkedRank_isNotNotRecorded() {
        val report = rankReport(
            "tenderfoot",
            rankProgress("tenderfoot", markedOn = LocalDate.of(2025, 8, 1)),
            badgeCompleted("chess", LocalDate.of(2026, 5, 1))
        )!!

        assertFalse(report.requirement("2").notRecorded)
        assertTrue(report.requirement("1").notRecorded)
    }

    @Test
    fun rankOnVersionMissingFromCatalog_hasNoReport() {
        val progress = BadgeProgressDetails(
            BadgeProgress("scout", LocalDate.of(2020, 1, 1), started),
            emptyList(),
            emptyList()
        )

        assertNull(rankReport("scout", progress))
    }
}
