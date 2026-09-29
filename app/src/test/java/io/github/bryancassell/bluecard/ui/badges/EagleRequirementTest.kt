package io.github.bryancassell.bluecard.ui.badges

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EagleRequirementTest {
    private fun badge(name: String, eagleRequired: Boolean = false, eagleGroup: String? = null) =
        MeritBadge(
            id = name.lowercase().replace(' ', '-'),
            name = name,
            summary = "Our summary of $name.",
            officialUrl = "https://www.scouting.org/merit-badges/",
            eagleRequired = eagleRequired,
            eagleGroup = eagleGroup,
            requirementVersions = listOf(
                RequirementsVersion(LocalDate.of(2026, 1, 1), listOf(Requirement("1", "First.")))
            )
        )

    private fun inGroup(name: String, group: String = "c-h-s") =
        badge(name, eagleRequired = true, eagleGroup = group)

    private val camping = badge("Camping", eagleRequired = true)
    private val chess = badge("Chess")

    @Test
    fun eagleGroups_namesEachGroupsBadgesInOrder_andLeavesOutOthers() {
        val catalog = listOf(
            inGroup("Swimming"),
            camping,
            inGroup("Cycling"),
            chess,
            inGroup("Hiking"),
            inGroup("Emergency Preparedness", group = "ep-l"),
            inGroup("Lifesaving", group = "ep-l")
        )

        assertEquals(
            mapOf(
                "c-h-s" to listOf("Cycling", "Hiking", "Swimming"),
                "ep-l" to listOf("Emergency Preparedness", "Lifesaving")
            ),
            catalog.eagleGroups()
        )
    }

    @Test
    fun eagleGroups_sortIgnoringCaseAndAccents() {
        val catalog = listOf(inGroup("Zoology"), inGroup("Écologie"), inGroup("bird Study"))

        assertEquals(
            listOf("bird Study", "Écologie", "Zoology"),
            catalog.eagleGroups().getValue("c-h-s")
        )
    }

    @Test
    fun notEagleRequired_isNull() {
        assertNull(chess.eagleRequirement(emptyMap()))
    }

    @Test
    fun eagleRequiredWithoutGroup_isRequired() {
        assertEquals(EagleRequirement.Required, camping.eagleRequirement(emptyMap()))
    }

    @Test
    fun onlyBadgeInItsGroupSoFar_isRequired() {
        val cycling = inGroup("Cycling")

        assertEquals(
            EagleRequirement.Required,
            cycling.eagleRequirement(listOf(cycling).eagleGroups())
        )
    }

    @Test
    fun badgeInGroup_isOneOfItsBadges() {
        val catalog = listOf(inGroup("Swimming"), inGroup("Cycling"), inGroup("Hiking"))

        assertEquals(
            EagleRequirement.OneOf(listOf("Cycling", "Hiking", "Swimming")),
            catalog[0].eagleRequirement(catalog.eagleGroups())
        )
    }
}
