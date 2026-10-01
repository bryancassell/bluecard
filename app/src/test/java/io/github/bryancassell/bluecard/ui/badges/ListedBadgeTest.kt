package io.github.bryancassell.bluecard.ui.badges

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.progress.BadgeStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
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
            RequirementsVersion(LocalDate.of(2026, 1, 1), listOf(Requirement("1", "First.")))
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

    @Test
    fun toListItem_showsTheBadgeWithTheGivenStatus() {
        val listed = ListedBadge(cycling, EagleRequirement.OneOf(listOf("Cycling", "Hiking")))

        assertEquals(
            BadgeListItem(
                id = "cycling",
                name = "Cycling",
                eagle = EagleRequirement.OneOf(listOf("Cycling", "Hiking")),
                status = BadgeStatus.Completed
            ),
            listed.toListItem(BadgeStatus.Completed)
        )
    }
}
