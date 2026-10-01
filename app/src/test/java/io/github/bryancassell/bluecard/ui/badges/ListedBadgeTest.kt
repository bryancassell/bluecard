package io.github.bryancassell.bluecard.ui.badges

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.progress.BadgeProgress
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.BadgeStatus
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListedBadgeTest {
    private fun badge(
        id: String,
        name: String,
        eagleRequired: Boolean = false,
        eagleGroup: String? = null
    ) = MeritBadge(
        id = id,
        name = name,
        summary = "Our summary of $name.",
        officialUrl = "https://www.scouting.org/merit-badges/",
        eagleRequired = eagleRequired,
        eagleGroup = eagleGroup,
        requirementVersions = listOf(
            RequirementsVersion(
                VERSION,
                listOf(Requirement("1", "First."), Requirement("2", "Second."))
            )
        )
    )

    private val camping = badge("camping", "Camping", eagleRequired = true)
    private val chess = badge("chess", "Chess")
    private val cycling = badge("cycling", "Cycling", eagleRequired = true, eagleGroup = "c-h-s")
    private val hiking = badge("hiking", "Hiking", eagleRequired = true, eagleGroup = "c-h-s")

    @Test
    fun inListOrder_sortsByName_withEachBadgesEagleRequirement() {
        // Ids that sort differently from the names.
        val zoology = badge("a", "Zoology")
        val catalog = listOf(hiking, zoology, chess, camping, cycling)

        assertEquals(
            listOf(
                ListedBadge(camping, EagleRequirement.Required),
                ListedBadge(chess, eagle = null),
                ListedBadge(cycling, EagleRequirement.OneOf(listOf("Cycling", "Hiking"))),
                ListedBadge(hiking, EagleRequirement.OneOf(listOf("Cycling", "Hiking"))),
                ListedBadge(zoology, eagle = null)
            ),
            catalog.inListOrder()
        )
    }

    private fun started(
        vararg completed: String,
        version: LocalDate = VERSION,
        completedOnPriorDate: LocalDate? = null
    ) = BadgeProgressDetails(
        BadgeProgress("cycling", version, version, completedOnPriorDate = completedOnPriorDate),
        completed.map { RequirementProgress("cycling", it, completed = true) },
        trackerEntries = emptyList()
    )

    private val listedCycling =
        ListedBadge(cycling, EagleRequirement.OneOf(listOf("Cycling", "Hiking")))

    @Test
    fun toListItem_notStarted_hasNoBar() {
        assertEquals(
            BadgeListItem(
                id = "cycling",
                name = "Cycling",
                eagle = EagleRequirement.OneOf(listOf("Cycling", "Hiking")),
                status = BadgeStatus.NotStarted,
                fractionDone = null
            ),
            listedCycling.toListItem(progress = null)
        )
    }

    @Test
    fun toListItem_inProgress_saysHowMuchIsDone() {
        val item = listedCycling.toListItem(started("2"))

        assertEquals(BadgeStatus.InProgress, item.status)
        assertEquals(0.5f, item.fractionDone)
    }

    @Test
    fun toListItem_startedWithNothingDone_isNothingDone() {
        assertEquals(0f, listedCycling.toListItem(started()).fractionDone)
    }

    @Test
    fun toListItem_completed_hasNoBar() {
        val item = listedCycling.toListItem(started("1", "2"))

        assertEquals(BadgeStatus.Completed, item.status)
        assertNull(item.fractionDone)
    }

    @Test
    fun toListItem_completedOnPriorDate_hasNoBar() {
        val priorDate = LocalDate.of(2026, 3, 1)
        val item = listedCycling.toListItem(started(completedOnPriorDate = priorDate))

        assertEquals(BadgeStatus.Completed, item.status)
        assertNull(item.fractionDone)
    }

    @Test
    fun toListItem_onAVersionMissingFromTheCatalog_isInProgressWithNoBar() {
        val item = listedCycling.toListItem(started("1", version = LocalDate.of(2020, 1, 1)))

        assertEquals(BadgeStatus.InProgress, item.status)
        assertNull(item.fractionDone)
    }

    private companion object {
        val VERSION: LocalDate = LocalDate.of(2026, 1, 1)
    }
}
