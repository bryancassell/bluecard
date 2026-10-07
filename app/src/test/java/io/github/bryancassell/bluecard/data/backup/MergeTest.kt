package io.github.bryancassell.bluecard.data.backup

import io.github.bryancassell.bluecard.data.progress.BadgeProgress
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.Counselor
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import io.github.bryancassell.bluecard.data.progress.TrackerEntry
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MergeTest {
    private val version = LocalDate.of(2026, 1, 1)
    private val started = LocalDate.of(2026, 3, 1)
    private val day = LocalDate.of(2026, 4, 15)

    private fun progress(
        badgeId: String = "camping",
        badge: BadgeProgress = BadgeProgress(badgeId, version, started),
        requirements: List<RequirementProgress> = emptyList(),
        trackerEntries: List<TrackerEntry> = emptyList()
    ) = BadgeProgressDetails(badge, requirements, trackerEntries)

    private fun requirement(number: String, comment: String? = null) =
        RequirementProgress("camping", number, completed = true, completedDate = day, comment)

    private fun row(
        id: Long,
        number: String,
        row: Int? = null,
        value: String,
        badgeId: String = "camping"
    ) = TrackerEntry(
        id = id,
        badgeId = badgeId,
        requirementNumber = number,
        rowNumber = row,
        values = mapOf("note" to value)
    )

    @Test
    fun mergeConflicts_leavesOutBadgesStartedOnOnlyOneSide() {
        val phone = listOf(progress("archery"), progress("camping"))
        val file = listOf(progress("camping"), progress("hiking"))

        assertEquals(emptySet<String>(), mergeConflicts(phone, file))
    }

    @Test
    fun mergeConflicts_leavesOutTheSameProgress_inAnotherOrder_withOtherEntryIds() {
        val phone = progress(
            requirements = listOf(requirement("4b"), requirement("5")),
            trackerEntries = listOf(
                row(7, "9a", row = 2, value = "second"),
                row(3, "9a", row = 1, value = "first"),
                row(4, "8", value = "log 1"),
                row(9, "8", value = "log 2")
            )
        )
        // As an export lists it: rows in the order they were saved, and every ID 0.
        val file = progress(
            requirements = listOf(requirement("5"), requirement("4b")),
            trackerEntries = listOf(
                row(0, "8", value = "log 1"),
                row(0, "9a", row = 2, value = "second"),
                row(0, "8", value = "log 2"),
                row(0, "9a", row = 1, value = "first")
            )
        )

        assertEquals(emptySet<String>(), mergeConflicts(listOf(phone), listOf(file)))
    }

    @Test
    fun mergeConflicts_hasEachBadgeWithDifferentProgress() {
        fun hikingRow(id: Long, value: String) = row(id, "8", value = value, badgeId = "hiking")
        val phone = listOf(
            progress("archery"),
            progress("camping", requirements = listOf(requirement("4b"))),
            progress("cooking"),
            progress("hiking", trackerEntries = listOf(hikingRow(1, "log 1"))),
            progress("swimming")
        )
        val file = listOf(
            // A different requirements version, counselor, requirement and tracker row.
            progress("archery", BadgeProgress("archery", day, started)),
            progress("camping", requirements = listOf(requirement("4b", comment = "Rain"))),
            progress("cooking", BadgeProgress("cooking", version, started, Counselor("Pat"))),
            progress("hiking", trackerEntries = listOf(hikingRow(0, "log 2"))),
            progress("swimming")
        )

        assertEquals(
            setOf("archery", "camping", "cooking", "hiking"),
            mergeConflicts(phone, file)
        )
    }

    @Test
    fun mergeConflicts_hasALogWithItsEntriesInAnotherOrder() {
        val phone = progress(
            trackerEntries = listOf(row(1, "8", value = "log 1"), row(2, "8", value = "log 2"))
        )
        val file = progress(
            trackerEntries = listOf(row(0, "8", value = "log 2"), row(0, "8", value = "log 1"))
        )

        assertEquals(setOf("camping"), mergeConflicts(listOf(phone), listOf(file)))
    }
}
