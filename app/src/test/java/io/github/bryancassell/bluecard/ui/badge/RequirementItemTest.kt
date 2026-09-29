package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RequirementItemTest {
    private val leaf = Requirement("1", "Plan a campout.")
    private val twoOfThree = Requirement(
        "2",
        "Do two of these.",
        requiredCount = 2,
        children = listOf(
            Requirement("2a", "Cook a meal."),
            Requirement("2b", "Lead a hike."),
            Requirement("2c", "Pitch a tent.")
        )
    )

    private fun done(vararg numbers: String) =
        numbers.associateWith { RequirementProgress("camping", it, completed = true) }

    @Test
    fun leaf_hasNoSubRequirements() {
        assertEquals(
            RequirementItem("1", "Plan a campout.", null, false, hasSubRequirements = false),
            leaf.toItem(emptyMap())
        )
    }

    @Test
    fun leaf_markedComplete_isCompleted() {
        assertTrue(leaf.toItem(done("1")).completed)
    }

    @Test
    fun someOfItsSubRequirements_isChoiceWithSubRequirements() {
        assertEquals(
            RequirementItem(
                "2",
                "Do two of these.",
                Choice(2, 3),
                false,
                hasSubRequirements = true
            ),
            twoOfThree.toItem(emptyMap())
        )
    }

    @Test
    fun choice_isCompletedOnceEnoughAreDone() {
        assertFalse(twoOfThree.toItem(done("2a")).completed)
        assertTrue(twoOfThree.toItem(done("2a", "2c")).completed)
    }

    @Test
    fun countOfAllItsSubRequirements_isNoChoice() {
        val both = Requirement(
            "3",
            "Do both.",
            requiredCount = 2,
            children = listOf(Requirement("3a", "A."), Requirement("3b", "B."))
        )

        assertNull(both.toItem(emptyMap()).choice)
    }

    @Test
    fun trackerOnly_isMarkedCompleteByScout() {
        val log = Requirement(
            "4",
            "Keep a camping log.",
            tracker = TrackerDefinition(
                listOf(TrackerColumn("night", "Night", TrackerColumnType.DATE))
            )
        )

        assertFalse(log.toItem(emptyMap()).hasSubRequirements)
    }
}
