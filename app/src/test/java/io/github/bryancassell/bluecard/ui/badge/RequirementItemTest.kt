package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import io.github.bryancassell.bluecard.data.progress.TrackerEntry
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
    fun leaf_isMarkedByHand() {
        assertEquals(
            RequirementItem("1", "Plan a campout.", null, false, markedByHand = true),
            leaf.toItem(emptyMap(), emptyMap())
        )
    }

    @Test
    fun leaf_markedComplete_isCompleted() {
        assertTrue(leaf.toItem(done("1"), emptyMap()).completed)
    }

    @Test
    fun partOfCompletedRequirement_whenNotComplete_isNotNeeded() {
        assertTrue(leaf.toItem(emptyMap(), emptyMap(), partOfHasEnough = true).notNeeded)
    }

    @Test
    fun partOfCompletedRequirement_whenComplete_isCompletedRatherThanNotNeeded() {
        val item = leaf.toItem(done("1"), emptyMap(), partOfHasEnough = true)

        assertTrue(item.completed)
        assertFalse(item.notNeeded)
    }

    @Test
    fun someOfItsSubRequirements_isChoiceWithSubRequirements() {
        assertEquals(
            RequirementItem(
                "2",
                "Do two of these.",
                Choice(2, 3),
                false,
                markedByHand = false
            ),
            twoOfThree.toItem(emptyMap(), emptyMap())
        )
    }

    @Test
    fun choice_isCompletedOnceEnoughAreDone() {
        assertFalse(twoOfThree.toItem(done("2a"), emptyMap()).completed)
        assertTrue(twoOfThree.toItem(done("2a", "2c"), emptyMap()).completed)
    }

    private val ownWorkAndTwoOfThree = twoOfThree.copy(ownWork = "Pack your gear.")

    @Test
    fun ownWork_isMarkedOnItsOwn_andTheRequirementIsCompletedOnlyWithEnoughChildren() {
        val marked = ownWorkAndTwoOfThree.toItem(done("2"), emptyMap())
        assertEquals(OwnWork("Pack your gear.", completed = true), marked.ownWork)
        assertFalse(marked.markedByHand)
        assertFalse(marked.completed)

        val children = ownWorkAndTwoOfThree.toItem(done("2a", "2c"), emptyMap())
        assertEquals(OwnWork("Pack your gear.", completed = false), children.ownWork)
        assertFalse(children.completed)

        assertTrue(ownWorkAndTwoOfThree.toItem(done("2", "2a", "2c"), emptyMap()).completed)
    }

    @Test
    fun withoutOwnWork_hasNone() {
        assertNull(twoOfThree.toItem(done("2"), emptyMap()).ownWork)
        assertNull(leaf.toItem(done("1"), emptyMap()).ownWork)
    }

    @Test
    fun nothingComplete_isNotPartlyCompleted() {
        val item = twoOfThree.toItem(emptyMap(), emptyMap())

        assertFalse(item.partlyCompleted)
        assertNull(item.completeCount)
    }

    @Test
    fun someSubRequirementsComplete_isPartlyCompleted_countedTowardTheNumberNeeded() {
        val item = twoOfThree.toItem(done("2a"), emptyMap())

        assertTrue(item.partlyCompleted)
        assertEquals(CompleteCount(1, 2), item.completeCount)
    }

    @Test
    fun allSubRequirementsNeeded_countsTowardAllOfThem() {
        val all = twoOfThree.copy(requiredCount = null)

        assertEquals(CompleteCount(2, 3), all.toItem(done("2a", "2c"), emptyMap()).completeCount)
    }

    @Test
    fun complete_isNotPartlyCompleted() {
        val item = twoOfThree.toItem(done("2a", "2c"), emptyMap())

        assertFalse(item.partlyCompleted)
        assertNull(item.completeCount)
    }

    @Test
    fun enoughSubRequirementsButNotOwnWork_isPartlyCompleted_countingNoMoreThanNeeded() {
        val item = ownWorkAndTwoOfThree.toItem(done("2a", "2b", "2c"), emptyMap())

        assertTrue(item.partlyCompleted)
        assertEquals(CompleteCount(2, 2), item.completeCount)
    }

    @Test
    fun ownWorkComplete_isPartlyCompleted_withNoCount() {
        val item = ownWorkAndTwoOfThree.toItem(done("2"), emptyMap())

        assertTrue(item.partlyCompleted)
        assertNull(item.completeCount)
    }

    @Test
    fun requirementFurtherDownComplete_isPartlyCompleted_withNoCount() {
        val deep = Requirement(
            "7",
            "Do these.",
            children = listOf(
                Requirement(
                    "7a",
                    "Do these too.",
                    children = listOf(Requirement("7a(1)", "One."), Requirement("7a(2)", "Two."))
                ),
                Requirement("7b", "B.")
            )
        )

        val item = deep.toItem(done("7a(1)"), emptyMap())

        assertTrue(item.partlyCompleted)
        assertNull(item.completeCount)
    }

    @Test
    fun onBadgeCompletedOnPriorDate_whenStillNeeded_isNotRecorded() {
        fun Requirement.notRecorded(
            progress: Map<String, RequirementProgress> = emptyMap(),
            partOfHasEnough: Boolean = false,
            badgeCompletedOnPriorDate: Boolean = true
        ) = toItem(progress, emptyMap(), partOfHasEnough, badgeCompletedOnPriorDate).notRecorded

        assertTrue(leaf.notRecorded())
        assertTrue(twoOfThree.notRecorded())

        // Part of it was recorded.
        assertFalse(twoOfThree.notRecorded(done("2a")))
        assertFalse(leaf.notRecorded(done("1")))
        assertFalse(leaf.notRecorded(partOfHasEnough = true))
        assertFalse(leaf.notRecorded(badgeCompletedOnPriorDate = false))
    }

    @Test
    fun notNeeded_isNotPartlyCompleted() {
        val item = twoOfThree.toItem(done("2a"), emptyMap(), partOfHasEnough = true)

        assertTrue(item.notNeeded)
        assertFalse(item.partlyCompleted)
        assertNull(item.completeCount)
    }

    @Test
    fun countOfAllItsSubRequirements_isNoChoice() {
        val both = Requirement(
            "3",
            "Do both.",
            requiredCount = 2,
            children = listOf(Requirement("3a", "A."), Requirement("3b", "B."))
        )

        assertNull(both.toItem(emptyMap(), emptyMap()).choice)
    }

    private val log = Requirement(
        "4",
        "Keep a camping log.",
        tracker = TrackerDefinition(
            listOf(TrackerColumn("night", "Night", TrackerColumnType.DATE)),
            "night",
            "nights"
        )
    )

    @Test
    fun log_isMarkedByHand() {
        assertTrue(log.toItem(emptyMap(), emptyMap()).markedByHand)
        assertFalse(log.toItem(emptyMap(), emptyMap()).completesFromRows)
        assertTrue(log.toItem(done("4"), emptyMap()).completed)
    }

    private val weeks = Requirement(
        "5",
        "Save for two weeks.",
        tracker = TrackerDefinition(
            listOf(TrackerColumn("saved", "Saved", TrackerColumnType.NUMBER)),
            "week",
            "weeks",
            rowCount = 2
        )
    )

    private fun week(id: Long, rowNumber: Int) =
        TrackerEntry(id, "camping", "5", rowNumber, mapOf("saved" to "5"))

    @Test
    fun fixedRowTracker_isCompletedOnceEveryRowIsFilled() {
        assertFalse(weeks.toItem(emptyMap(), emptyMap()).markedByHand)
        assertTrue(weeks.toItem(emptyMap(), emptyMap()).completesFromRows)
        assertFalse(weeks.toItem(emptyMap(), mapOf("5" to listOf(week(1, 1)))).completed)
        assertTrue(weeks.toItem(emptyMap(), mapOf("5" to listOf(week(1, 1), week(2, 2)))).completed)
        // Marking it complete by hand doesn't count.
        assertFalse(weeks.toItem(done("5"), emptyMap()).completed)
    }

    @Test
    fun trackerPartlyFilled_isPartlyCompleted_untilComplete() {
        assertTrue(weeks.toItem(emptyMap(), mapOf("5" to listOf(week(1, 1)))).partlyCompleted)
        assertFalse(
            weeks.toItem(emptyMap(), mapOf("5" to listOf(week(1, 1), week(2, 2)))).partlyCompleted
        )
    }

    @Test
    fun tracker_countsItsOwnRequirementsEntries() {
        val entries = listOf(
            TrackerEntry(1, "camping", "4", values = mapOf("night" to "2026-05-01")),
            TrackerEntry(2, "camping", "4", values = mapOf("night" to "2026-05-02"))
        )

        assertEquals(
            TrackerCount(2, null, "nights"),
            log.toItem(emptyMap(), mapOf("4" to entries, "5" to entries.take(1))).tracker
        )
    }

    @Test
    fun noTracker_hasNoCount() {
        assertNull(leaf.toItem(emptyMap(), emptyMap()).tracker)
    }
}
