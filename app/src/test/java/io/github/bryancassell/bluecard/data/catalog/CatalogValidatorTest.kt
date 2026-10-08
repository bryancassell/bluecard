package io.github.bryancassell.bluecard.data.catalog

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogValidatorTest {
    private val requirement = Requirement(number = "1", summary = "Do the thing.")
    private val version = RequirementsVersion(LocalDate.of(2026, 1, 1), listOf(requirement))
    private val badge = MeritBadge(
        id = "first-aid",
        name = "First Aid",
        summary = "Learn to help in an emergency.",
        officialUrl = "https://www.scouting.org/merit-badges/first-aid/",
        requirementVersions = listOf(version)
    )
    private val rank = Rank(
        id = "tenderfoot",
        name = "Tenderfoot",
        summary = "Learn the basics of camping and first aid.",
        officialUrl = "https://www.scouting.org/tenderfoot/",
        requirementVersions = listOf(version)
    )
    private val tracker = TrackerDefinition(
        columns = listOf(TrackerColumn("date", "Date", TrackerColumnType.DATE)),
        rowLabel = "night",
        rowLabelPlural = "nights"
    )

    private fun errorsFor(
        vararg badges: MeritBadge,
        ranks: List<Rank> = emptyList(),
        formatVersion: Int = 1
    ) = CatalogValidator.validate(Catalog(formatVersion, badges.toList(), ranks))

    private fun errorsForRequirements(vararg requirements: Requirement) = errorsFor(
        badge.copy(
            requirementVersions = listOf(version.copy(requirements = requirements.toList()))
        )
    )

    @Test
    fun validCatalog_hasNoErrors() {
        val choice = Requirement(
            number = "2",
            summary = "Do one of these.",
            requiredCount = 1,
            ownWork = "Explain why.",
            children = listOf(
                Requirement(
                    number = "2a",
                    summary = "A.",
                    tracker = tracker.copy(
                        columns = tracker.columns + TrackerColumn(
                            "hours",
                            "Hours",
                            TrackerColumnType.NUMBER,
                            ColumnTotal(1, "hour", "hours")
                        )
                    )
                ),
                Requirement(
                    number = "2b",
                    summary = "B.",
                    ownWork = "Compare them.",
                    tracker = tracker.copy(rowCount = 3)
                )
            )
        )
        val eagleBadge = badge.copy(
            id = "hiking",
            eagleRequired = true,
            eagleGroup = "cycling-hiking-swimming",
            requirementVersions = listOf(
                version.copy(requirements = listOf(requirement, choice)),
                version.copy(effectiveDate = LocalDate.of(2025, 1, 1))
            )
        )
        val secondClass = rank.copy(
            id = "second-class",
            name = "Second Class",
            requirementVersions = listOf(
                version.copy(
                    requirements = listOf(
                        requirement.copy(monthsInRank = 4),
                        Requirement("2", "Earn a badge.", meritBadges = MeritBadgesNeeded(1, 1))
                    )
                )
            )
        )
        assertEquals(
            emptyList<String>(),
            errorsFor(badge, eagleBadge, ranks = listOf(rank, secondClass))
        )
    }

    @Test
    fun unsupportedFormatVersion() {
        assertEquals(
            listOf("formatVersion is 2; this app reads 1"),
            errorsFor(badge, formatVersion = 2)
        )
    }

    @Test
    fun noBadges() {
        assertEquals(listOf("the catalog has no badges"), errorsFor())
    }

    @Test
    fun duplicateBadgeId() {
        assertEquals(
            listOf("badge id \"first-aid\" is used more than once"),
            errorsFor(badge, badge)
        )
    }

    @Test
    fun duplicateRankId() {
        assertEquals(
            listOf("rank id \"tenderfoot\" is used more than once"),
            errorsFor(badge, ranks = listOf(rank, rank))
        )
    }

    @Test
    fun rankIdUsedByABadge() {
        assertEquals(
            listOf("id \"first-aid\" is used by both a badge and a rank"),
            errorsFor(badge, ranks = listOf(rank.copy(id = "first-aid")))
        )
    }

    @Test
    fun badgeFieldProblems() {
        val bad = badge.copy(
            id = "First Aid",
            name = " ",
            summary = "",
            officialUrl = "http://example.com/first-aid",
            eagleGroup = "group"
        )
        val where = "badge \"First Aid\""
        assertEquals(
            listOf(
                "$where: id must be lowercase words joined by '-'",
                "$where: name is blank",
                "$where: summary is blank",
                "$where: officialUrl must start with https://www.scouting.org/merit-badges/",
                "$where: has an eagleGroup but is not eagleRequired"
            ),
            errorsFor(bad)
        )
    }

    @Test
    fun testLabPilotBadge() {
        val pilot = badge.copy(
            officialUrl = "https://www.scouting.org/skills/merit-badges/test-lab/dance/"
        )
        assertEquals(
            listOf(
                "badge \"first-aid\": officialUrl must start with " +
                    "https://www.scouting.org/merit-badges/"
            ),
            errorsFor(pilot)
        )
    }

    @Test
    fun rankFieldProblems() {
        val bad = rank.copy(
            id = "Second Class",
            name = " ",
            summary = "",
            officialUrl = "http://example.com/second-class",
            requirementVersions = listOf(version, version.copy(requirements = emptyList()))
        )
        val where = "rank \"Second Class\""
        assertEquals(
            listOf(
                "$where: id must be lowercase words joined by '-'",
                "$where: name is blank",
                "$where: summary is blank",
                "$where: officialUrl must start with https://www.scouting.org/",
                "$where: more than one version effective 2026-01-01",
                "$where, version 2026-01-01: has no requirements"
            ),
            errorsFor(badge, ranks = listOf(bad))
        )
    }

    @Test
    fun rankWithoutRequirementVersions() {
        assertEquals(
            listOf("rank \"tenderfoot\": has no requirement versions"),
            errorsFor(badge, ranks = listOf(rank.copy(requirementVersions = emptyList())))
        )
    }

    @Test
    fun noRequirementVersions() {
        assertEquals(
            listOf("badge \"first-aid\": has no requirement versions"),
            errorsFor(badge.copy(requirementVersions = emptyList()))
        )
    }

    @Test
    fun duplicateEffectiveDate() {
        assertEquals(
            listOf("badge \"first-aid\": more than one version effective 2026-01-01"),
            errorsFor(badge.copy(requirementVersions = listOf(version, version)))
        )
    }

    @Test
    fun versionWithoutRequirements() {
        assertEquals(
            listOf("badge \"first-aid\", version 2026-01-01: has no requirements"),
            errorsForRequirements()
        )
    }

    @Test
    fun duplicateRequirementNumber_anywhereInTheTree() {
        val parent = Requirement(
            number = "2",
            summary = "Parent.",
            children = listOf(Requirement(number = "1", summary = "Clashes with the top level."))
        )
        assertEquals(
            listOf(
                "badge \"first-aid\", version 2026-01-01: requirement number \"1\" is used more than once"
            ),
            errorsForRequirements(requirement, parent)
        )
    }

    @Test
    fun letteredTopLevelRequirement() {
        val nested = Requirement(
            number = "2",
            summary = "Parent.",
            children = listOf(Requirement(number = "2a", summary = "Nested, so it's fine."))
        )
        assertEquals(
            listOf(
                "badge \"first-aid\", version 2026-01-01, requirement \"1a\": " +
                    "is at the top level, so its number must be a whole number"
            ),
            errorsForRequirements(Requirement(number = "1a", summary = "Has no parent."), nested)
        )
    }

    @Test
    fun blankRequirementFields() {
        assertEquals(
            listOf(
                "badge \"first-aid\", version 2026-01-01, requirement \"\": number is blank",
                "badge \"first-aid\", version 2026-01-01, requirement \"\": summary is blank"
            ),
            errorsForRequirements(Requirement(number = "", summary = " "))
        )
    }

    @Test
    fun requiredCountOutOfRange() {
        val children = listOf(
            Requirement(number = "1a", summary = "A."),
            Requirement(number = "1b", summary = "B.")
        )
        val where = "badge \"first-aid\", version 2026-01-01, requirement \"1\""
        assertEquals(
            listOf("$where: requiredCount is 3 but it has 2 children"),
            errorsForRequirements(requirement.copy(requiredCount = 3, children = children))
        )
        assertEquals(
            listOf("$where: requiredCount is 0 but it has 2 children"),
            errorsForRequirements(requirement.copy(requiredCount = 0, children = children))
        )
        assertEquals(
            listOf("$where: requiredCount is 1 but it has 0 children"),
            errorsForRequirements(requirement.copy(requiredCount = 1))
        )
    }

    @Test
    fun ownWorkWithoutChildrenOrFixedRowsOrBlank() {
        val where = "badge \"first-aid\", version 2026-01-01, requirement \"1\""
        assertEquals(
            listOf("$where: ownWork but it has no children or fixed-row tracker"),
            errorsForRequirements(requirement.copy(ownWork = "Explain why."))
        )
        // A log's requirement has a checkbox of its own.
        assertEquals(
            listOf("$where: ownWork but it has no children or fixed-row tracker"),
            errorsForRequirements(requirement.copy(ownWork = "Explain why.", tracker = tracker))
        )
        val children = listOf(Requirement(number = "1a", summary = "A."))
        assertEquals(
            listOf("$where: ownWork is blank"),
            errorsForRequirements(requirement.copy(ownWork = " ", children = children))
        )
    }

    @Test
    fun monthsInRank_lessThanOne() {
        val active = requirement.copy(monthsInRank = 0)
        val secondClass = rank.copy(
            id = "second-class",
            requirementVersions = listOf(version.copy(requirements = listOf(active)))
        )
        assertEquals(
            listOf(
                "rank \"second-class\", version 2026-01-01, requirement \"1\": " +
                    "monthsInRank must be at least 1"
            ),
            errorsFor(badge, ranks = listOf(rank, secondClass))
        )
    }

    @Test
    fun monthsInRank_onTheLowestRank() {
        val active = requirement.copy(
            children = listOf(Requirement("1a", "Be active.", monthsInRank = 4))
        )
        val lowest = rank.copy(
            requirementVersions = listOf(version.copy(requirements = listOf(active)))
        )
        assertEquals(
            listOf(
                "rank \"tenderfoot\", version 2026-01-01, requirement \"1a\": " +
                    "has monthsInRank but it's the lowest rank"
            ),
            errorsFor(badge, ranks = listOf(lowest, rank.copy(id = "second-class")))
        )
    }

    @Test
    fun monthsInRank_onABadge() {
        assertEquals(
            listOf(
                "badge \"first-aid\", version 2026-01-01, requirement \"1\": " +
                    "has monthsInRank but it isn't a rank's"
            ),
            errorsForRequirements(requirement.copy(monthsInRank = 4))
        )
    }

    @Test
    fun meritBadges_onABadge() {
        val eagleBadge = badge.copy(id = "camping", eagleRequired = true)
        assertEquals(
            listOf(
                "badge \"first-aid\", version 2026-01-01, requirement \"1\": " +
                    "has meritBadges but it isn't a rank's"
            ),
            errorsFor(
                badge.copy(
                    requirementVersions = listOf(
                        version.copy(
                            requirements = listOf(
                                requirement.copy(meritBadges = MeritBadgesNeeded(1, 1))
                            )
                        )
                    )
                ),
                eagleBadge
            )
        )
    }

    @Test
    fun meritBadges_withChildrenAndATracker() {
        val where = "rank \"tenderfoot\", version 2026-01-01, requirement \"1\""
        assertEquals(
            listOf(
                "$where: has meritBadges and children",
                "$where: has meritBadges and a tracker"
            ),
            errorsForRankRequirement(
                requirement.copy(
                    meritBadges = MeritBadgesNeeded(1, 1),
                    children = listOf(Requirement("1a", "A.")),
                    tracker = tracker
                )
            )
        )
    }

    @Test
    fun meritBadges_malformedCounts() {
        val where = "rank \"tenderfoot\", version 2026-01-01, requirement \"1\""
        val between = "$where: meritBadges eagleRequired must be between 1 and its total"
        assertEquals(
            listOf("$where: meritBadges total must be at least 1", between),
            errorsForRankRequirement(requirement.copy(meritBadges = MeritBadgesNeeded(0, 1)))
        )
        assertEquals(
            listOf(between),
            errorsForRankRequirement(requirement.copy(meritBadges = MeritBadgesNeeded(2, 0)))
        )
        assertEquals(
            listOf(between),
            errorsForRankRequirement(requirement.copy(meritBadges = MeritBadgesNeeded(1, 2)))
        )
    }

    @Test
    fun meritBadges_moreEagleRequiredThanTheCatalogHas() {
        val cycling = badge.copy(id = "cycling", eagleRequired = true, eagleGroup = "c-h-s")
        val hiking = badge.copy(id = "hiking", eagleRequired = true, eagleGroup = "c-h-s")

        // First Aid, not Eagle-required here, so there are enough badges for a total of 3.
        fun errorsAskingFor(needed: MeritBadgesNeeded) = errorsFor(
            badge,
            cycling,
            hiking,
            ranks = listOf(
                rank.copy(
                    requirementVersions = listOf(
                        version.copy(requirements = listOf(requirement.copy(meritBadges = needed)))
                    )
                )
            )
        )
        val where = "rank \"tenderfoot\", version 2026-01-01, requirement \"1\""

        assertEquals(emptyList<String>(), errorsAskingFor(MeritBadgesNeeded(2, 2)))
        assertEquals(
            listOf("$where: meritBadges asks for 3 Eagle-required badges, but the catalog has 2"),
            errorsAskingFor(MeritBadgesNeeded(3, 3))
        )
        // Two badges, but one the scout needs, since they're in a group.
        assertEquals(
            listOf(
                "$where: meritBadges asks for 2 Eagle-required badges, but the catalog has 1, " +
                    "counting each group once"
            ),
            errorsAskingFor(MeritBadgesNeeded(2, 2, eagleGroupsCountOnce = true))
        )
    }

    @Test
    fun meritBadges_moreThanTheCatalogHas() {
        assertEquals(
            listOf(
                "rank \"tenderfoot\", version 2026-01-01, requirement \"1\": " +
                    "meritBadges asks for 3 badges, but the catalog has 2"
            ),
            errorsForRankRequirement(requirement.copy(meritBadges = MeritBadgesNeeded(3, 1)))
        )
    }

    /** The errors for a catalog with two Eagle-required badges and a rank with [requirement]. */
    private fun errorsForRankRequirement(requirement: Requirement) = errorsFor(
        badge.copy(eagleRequired = true),
        badge.copy(id = "camping", eagleRequired = true),
        ranks = listOf(
            rank.copy(
                requirementVersions = listOf(version.copy(requirements = listOf(requirement)))
            )
        )
    )

    @Test
    fun malformedTracker() {
        val bad = TrackerDefinition(
            columns = listOf(
                TrackerColumn("date", "Date", TrackerColumnType.DATE),
                TrackerColumn("date", "", TrackerColumnType.TEXT),
                TrackerColumn("Miles Walked", "Miles", TrackerColumnType.NUMBER)
            ),
            rowLabel = "",
            rowLabelPlural = "Hikes",
            rowCount = 0
        )
        val where = "badge \"first-aid\", version 2026-01-01, requirement \"1\", tracker"
        assertEquals(
            listOf(
                "$where: column id \"date\" is used more than once",
                "$where: column \"date\" has a blank label",
                "$where: column id \"Miles Walked\" must be lowercase words joined by '-'",
                "$where: rowCount must be at least 1",
                "$where: rowLabel is blank",
                "$where: rowLabelPlural \"Hikes\" must start with a lowercase letter"
            ),
            errorsForRequirements(requirement.copy(tracker = bad))
        )
    }

    @Test
    fun malformedTotal() {
        val total = ColumnTotal(needed = 0, label = "", labelPlural = "Hours")
        val bad = tracker.copy(
            columns = listOf(
                TrackerColumn("hours", "Hours", TrackerColumnType.NUMBER, total),
                TrackerColumn(
                    "notes",
                    "Notes",
                    TrackerColumnType.TEXT,
                    ColumnTotal(1, "hour", "hours")
                )
            )
        )
        val where = "badge \"first-aid\", version 2026-01-01, requirement \"1\", tracker"
        assertEquals(
            listOf(
                "$where, column \"hours\": total needed must be at least 1",
                "$where, column \"hours\": total label is blank",
                "$where, column \"hours\": total labelPlural \"Hours\" must start with a lowercase letter",
                "$where, column \"notes\": has a total but isn't a number"
            ),
            errorsForRequirements(requirement.copy(tracker = bad))
        )
    }

    @Test
    fun totalOnATrackerWithAFixedNumberOfRows() {
        val hours =
            TrackerColumn(
                "hours",
                "Hours",
                TrackerColumnType.NUMBER,
                ColumnTotal(6, "hour", "hours")
            )
        val weeks = tracker.copy(columns = listOf(hours), rowCount = 12)
        assertEquals(
            listOf(
                "badge \"first-aid\", version 2026-01-01, requirement \"1\", tracker: " +
                    "has a total but a fixed number of rows"
            ),
            errorsForRequirements(requirement.copy(tracker = weeks))
        )
    }

    @Test
    fun rowLabelsNotStartingWithALowercaseLetter() {
        val where = "badge \"first-aid\", version 2026-01-01, requirement \"1\", tracker"
        assertEquals(
            listOf(
                "$where: rowLabel \" week\" must start with a lowercase letter",
                "$where: rowLabelPlural \"3 weeks\" must start with a lowercase letter"
            ),
            errorsForRequirements(
                requirement.copy(
                    tracker = tracker.copy(rowLabel = " week", rowLabelPlural = "3 weeks")
                )
            )
        )
    }

    @Test
    fun trackerWithoutColumns() {
        assertEquals(
            listOf(
                "badge \"first-aid\", version 2026-01-01, requirement \"1\", tracker: has no columns"
            ),
            errorsForRequirements(
                requirement.copy(tracker = tracker.copy(columns = emptyList()))
            )
        )
    }
}
