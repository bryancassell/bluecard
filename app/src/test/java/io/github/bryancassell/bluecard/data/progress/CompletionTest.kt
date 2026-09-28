package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompletionTest {
    private fun day(n: Int) = LocalDate.of(2026, 5, n)

    private fun leaf(number: String) = Requirement(number = number, summary = "Do $number.")

    private fun done(number: String, date: LocalDate? = null) =
        RequirementProgress(BADGE, number, completed = true, completedDate = date)

    private fun progressOf(vararg progress: RequirementProgress) =
        progress.associateBy { it.requirementNumber }

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
    fun leaf_completeOnlyWhenMarked() {
        assertNull(leaf("1").completion(progressOf()))
        assertNull(
            leaf("1").completion(progressOf(RequirementProgress(BADGE, "1", comment = "Started")))
        )
        assertEquals(Completion(day(3)), leaf("1").completion(progressOf(done("1", day(3)))))
        assertEquals(Completion(null), leaf("1").completion(progressOf(done("1"))))
    }

    @Test
    fun allChildrenRequired_completeWhenAllDone_onLastDate() {
        assertNull(allOf.completion(progressOf(done("2a", day(1)))))
        assertEquals(
            Completion(day(9)),
            allOf.completion(progressOf(done("2a", day(9)), done("2b", day(4))))
        )
    }

    @Test
    fun allChildrenRequired_anUndatedChildMeansNoDate() {
        assertEquals(Completion(null), allOf.completion(progressOf(done("2a", day(9)), done("2b"))))
    }

    @Test
    fun choice_completeWhenEnoughChildrenDone() {
        assertNull(twoOf.completion(progressOf(done("3a", day(1)))))
        assertEquals(
            Completion(day(5)),
            twoOf.completion(progressOf(done("3a", day(1)), done("3c", day(5))))
        )
    }

    @Test
    fun choice_withExtraChildrenDone_datedWhenEnoughWereDone() {
        // Complete once the second of the three was done, on the 5th.
        val progress = progressOf(done("3a", day(9)), done("3b", day(1)), done("3c", day(5)))
        assertEquals(Completion(day(5)), twoOf.completion(progress))
    }

    @Test
    fun choice_undatedChildrenOnlyMatterIfTheyAreNeeded() {
        assertEquals(
            Completion(day(5)),
            twoOf.completion(progressOf(done("3a", day(1)), done("3b"), done("3c", day(5))))
        )
        assertEquals(
            Completion(null),
            twoOf.completion(progressOf(done("3a", day(1)), done("3b")))
        )
    }

    @Test
    fun nestedChoice_countsCompleteSubtrees() {
        // "Do two of 4a-4c", where 4c is itself "do two of 4c(1)-4c(3)".
        val nested = Requirement(
            number = "4",
            summary = "Do two.",
            requiredCount = 2,
            children = listOf(
                leaf("4a"),
                leaf("4b"),
                Requirement(
                    number = "4c",
                    summary = "Do two.",
                    requiredCount = 2,
                    children = listOf(leaf("4c(1)"), leaf("4c(2)"), leaf("4c(3)"))
                )
            )
        )
        assertNull(nested.completion(progressOf(done("4a", day(1)), done("4c(1)", day(2)))))
        assertEquals(
            Completion(day(7)),
            nested.completion(
                progressOf(done("4a", day(1)), done("4c(1)", day(2)), done("4c(3)", day(7)))
            )
        )
    }

    private val version = RequirementsVersion(day(1), listOf(leaf("1"), allOf, twoOf))

    private fun badge(
        vararg progress: RequirementProgress,
        completedOnPriorDate: LocalDate? = null
    ) = BadgeProgressDetails(
        BadgeProgress(BADGE, day(1), day(1), completedOnPriorDate = completedOnPriorDate),
        progress.toList(),
        emptyList()
    )

    @Test
    fun badge_incompleteUntilEveryTopLevelRequirementIs() {
        assertNull(badge().completion(version))
        assertNull(
            badge(done("1", day(2)), done("2a", day(3)), done("2b", day(4))).completion(version)
        )
    }

    @Test
    fun badge_completeOnLastRequirementDate() {
        val all = badge(
            done("1", day(2)),
            done("2a", day(3)),
            done("2b", day(8)),
            done("3a", day(4)),
            done("3b", day(6))
        )
        assertEquals(Completion(day(8)), all.completion(version))
    }

    @Test
    fun badge_completeWithoutDateWhenANeededDateIsMissing() {
        val all = badge(done("1"), done("2a", day(3)), done("2b", day(8)), done("3a"), done("3b"))
        assertEquals(Completion(null), all.completion(version))
    }

    @Test
    fun badge_completedOnPriorDate_isCompleteOnThatDate() {
        assertEquals(Completion(day(20)), badge(completedOnPriorDate = day(20)).completion(version))
        // The prior date wins even when requirement progress is also complete.
        assertEquals(
            Completion(day(20)),
            badge(done("1", day(2)), completedOnPriorDate = day(20)).completion(
                RequirementsVersion(day(1), listOf(leaf("1")))
            )
        )
    }

    private companion object {
        const val BADGE = "personal-fitness"
    }
}
