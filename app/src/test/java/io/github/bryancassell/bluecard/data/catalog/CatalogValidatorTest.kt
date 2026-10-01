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
    private val tracker = TrackerDefinition(
        columns = listOf(TrackerColumn("date", "Date", TrackerColumnType.DATE)),
        rowLabel = "night",
        rowLabelPlural = "nights"
    )

    private fun errorsFor(vararg badges: MeritBadge, formatVersion: Int = 1) =
        CatalogValidator.validate(Catalog(formatVersion, badges.toList()))

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
                Requirement(number = "2a", summary = "A."),
                Requirement(number = "2b", summary = "B.", tracker = tracker.copy(rowCount = 3))
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
        assertEquals(emptyList<String>(), errorsFor(badge, eagleBadge))
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
                "$where: officialUrl must start with https://www.scouting.org/",
                "$where: has an eagleGroup but is not eagleRequired"
            ),
            errorsFor(bad)
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
    fun ownWorkWithoutChildrenOrBlank() {
        val where = "badge \"first-aid\", version 2026-01-01, requirement \"1\""
        assertEquals(
            listOf("$where: ownWork but it has no children"),
            errorsForRequirements(requirement.copy(ownWork = "Explain why."))
        )
        val children = listOf(Requirement(number = "1a", summary = "A."))
        assertEquals(
            listOf("$where: ownWork is blank"),
            errorsForRequirements(requirement.copy(ownWork = " ", children = children))
        )
    }

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
