package io.github.bryancassell.bluecard.data.progress

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/**
 * Behavior every [ProgressRepository] must have. Runs against the Room implementation
 * (RoomProgressRepositoryTest) and the fake (FakeProgressRepositoryTest), so the fake that
 * other features' tests use behaves like the real one.
 */
abstract class ProgressRepositoryContract {
    protected abstract val repository: ProgressRepository

    private val version = LocalDate.of(2026, 1, 1)
    private val started = LocalDate.of(2026, 3, 1)
    private val day = LocalDate.of(2026, 4, 15)

    private suspend fun progress(badgeId: String = BADGE) =
        repository.observeProgress(badgeId).first()

    private suspend fun requirement(number: String) =
        progress()?.requirements?.singleOrNull { it.requirementNumber == number }

    private suspend fun trackerValues(number: String) = progress()!!.trackerEntries
        .filter { it.requirementNumber == number }.sortedBy { it.id }.map { it.values }

    private fun test(body: suspend () -> Unit): TestResult = runTest {
        repository.startBadge(BADGE, version, started)
        body()
    }

    @Test
    fun startBadge_recordsVersionAndDate() = test {
        assertEquals(
            BadgeProgressDetails(BadgeProgress(BADGE, version, started), emptyList(), emptyList()),
            progress()
        )
    }

    @Test
    fun startBadge_again_keepsExistingProgress() = test {
        repository.setCounselor(BADGE, Counselor(name = "Pat"))
        repository.startBadge(BADGE, LocalDate.of(2027, 1, 1), day)

        val badge = progress()!!.badge
        assertEquals(version, badge.requirementsVersion)
        assertEquals(started, badge.startedDate)
        assertEquals(Counselor(name = "Pat"), badge.counselor)
    }

    @Test
    fun observeProgress_unstartedBadge_isNull() = test {
        assertNull(progress("never-started"))
    }

    @Test
    fun observeAllProgress_listsStartedBadgesById() = test {
        repository.startBadge("archery", version, started)
        assertEquals(
            listOf("archery", BADGE),
            repository.observeAllProgress().first().map { it.badge.badgeId }
        )
    }

    @Test
    fun setCounselor_savesUpdatesAndClears() = test {
        repository.setCounselor(BADGE, Counselor("Pat Lee", "555-0100", "pat@example.com"))
        assertEquals(
            Counselor("Pat Lee", "555-0100", "pat@example.com"),
            progress()!!.badge.counselor
        )

        repository.setCounselor(BADGE, Counselor(name = "Sam"))
        assertEquals(Counselor(name = "Sam"), progress()!!.badge.counselor)

        repository.setCounselor(BADGE, null)
        assertNull(progress()!!.badge.counselor)
    }

    @Test
    fun setCompletedOnPriorDate_setsAndUndoes() = test {
        repository.setCompletedOnPriorDate(BADGE, day)
        assertEquals(day, progress()!!.badge.completedOnPriorDate)

        repository.setCompletedOnPriorDate(BADGE, null)
        assertNull(progress()!!.badge.completedOnPriorDate)
    }

    @Test
    fun markRequirementCompleted_withAndWithoutDate() = test {
        repository.markRequirementCompleted(BADGE, "1", day)
        repository.markRequirementCompleted(BADGE, "2", null)

        assertEquals(
            RequirementProgress(BADGE, "1", completed = true, completedDate = day),
            requirement("1")
        )
        assertEquals(RequirementProgress(BADGE, "2", completed = true), requirement("2"))
    }

    @Test
    fun markRequirementNotCompleted_removesDateButKeepsComment() = test {
        repository.setRequirementComment(BADGE, "1", "Hiked with my troop.")
        repository.markRequirementCompleted(BADGE, "1", day)

        repository.markRequirementNotCompleted(BADGE, "1")

        assertEquals(
            RequirementProgress(BADGE, "1", comment = "Hiked with my troop."),
            requirement("1")
        )
    }

    @Test
    fun setRequirementComment_keepsCompletion_andBlankRemovesIt() = test {
        repository.markRequirementCompleted(BADGE, "1", day)

        repository.setRequirementComment(BADGE, "1", "Done at camp.")
        assertEquals(RequirementProgress(BADGE, "1", true, day, "Done at camp."), requirement("1"))

        repository.setRequirementComment(BADGE, "1", "  ")
        assertEquals(RequirementProgress(BADGE, "1", true, day), requirement("1"))
    }

    @Test
    fun trackerEntries_addUpdateDelete() = test {
        val first = repository.addTrackerEntry(BADGE, "7a", mapOf("minutes" to "30"))
        val second = repository.addTrackerEntry(BADGE, "7a", mapOf("minutes" to "45"))
        assertEquals(
            listOf(mapOf("minutes" to "30"), mapOf("minutes" to "45")),
            trackerValues("7a")
        )

        repository.updateTrackerEntry(first, mapOf("minutes" to "35", "notes" to "Ran a mile"))
        repository.deleteTrackerEntry(second)

        assertEquals(listOf(mapOf("minutes" to "35", "notes" to "Ran a mile")), trackerValues("7a"))
    }

    @Test
    fun clearRequirement_removesOnlyThatRequirement() = test {
        repository.markRequirementCompleted(BADGE, "1", day)
        repository.setRequirementComment(BADGE, "7a", "Week 1 went well.")
        repository.addTrackerEntry(BADGE, "7a", mapOf("minutes" to "30"))
        repository.addTrackerEntry(BADGE, "7b", mapOf("mile" to "9:30"))

        repository.clearRequirement(BADGE, "7a")

        assertNull(requirement("7a"))
        assertEquals(emptyList<Map<String, String>>(), trackerValues("7a"))
        assertEquals(
            RequirementProgress(BADGE, "1", completed = true, completedDate = day),
            requirement("1")
        )
        assertEquals(listOf(mapOf("mile" to "9:30")), trackerValues("7b"))
    }

    @Test
    fun clearBadge_removesEverythingForThatBadgeOnly() = test {
        repository.startBadge("archery", version, started)
        repository.setCounselor(BADGE, Counselor(name = "Pat"))
        repository.markRequirementCompleted(BADGE, "1", day)
        repository.addTrackerEntry(BADGE, "7a", mapOf("minutes" to "30"))
        repository.markRequirementCompleted("archery", "1", day)

        repository.clearBadge(BADGE)

        assertNull(progress())
        assertEquals(1, progress("archery")!!.requirements.size)
        // Starting again begins from nothing: nothing recorded earlier comes back.
        repository.startBadge(BADGE, version, day)
        assertEquals(
            BadgeProgressDetails(BadgeProgress(BADGE, version, day), emptyList(), emptyList()),
            progress()
        )
    }

    @Test
    fun clearAll_removesAllProgress() = test {
        repository.startBadge("archery", version, started)
        repository.markRequirementCompleted(BADGE, "1", day)
        repository.addTrackerEntry("archery", "2", mapOf("score" to "250"))

        repository.clearAll()

        assertEquals(emptyList<BadgeProgressDetails>(), repository.observeAllProgress().first())
    }

    @Test
    fun observeProgress_emitsWhenProgressChanges() = test {
        val flow = repository.observeProgress(BADGE)
        flow.first { it != null }

        repository.markRequirementCompleted(BADGE, "1", day)

        flow.first { details -> details!!.requirements.any { it.completed } }
    }

    @Test
    fun recordingOnAnUnstartedBadge_failsAndRecordsNothing() = test {
        val writes: List<Pair<String, suspend () -> Unit>> = listOf(
            "setCounselor" to { repository.setCounselor(UNSTARTED, Counselor(name = "Pat")) },
            "setCompletedOnPriorDate" to { repository.setCompletedOnPriorDate(UNSTARTED, day) },
            "markRequirementCompleted" to {
                repository.markRequirementCompleted(UNSTARTED, "1", day)
            },
            "markRequirementNotCompleted" to {
                repository.markRequirementNotCompleted(UNSTARTED, "1")
            },
            "setRequirementComment" to { repository.setRequirementComment(UNSTARTED, "1", "Hi") },
            "addTrackerEntry" to {
                repository.addTrackerEntry(UNSTARTED, "7a", mapOf("minutes" to "30"))
            }
        )
        for ((name, write) in writes) {
            try {
                write()
                fail("$name should fail for a badge that hasn't been started")
            } catch (e: IllegalStateException) {
                assertEquals(notStartedError(UNSTARTED).message, e.message)
            }
        }
        assertNull(progress(UNSTARTED))
    }

    @Test
    fun clearingOrChangingSomethingMissing_doesNothing() = test {
        repository.markRequirementCompleted(BADGE, "1", day)
        val before = progress()

        repository.clearRequirement(UNSTARTED, "1")
        repository.clearBadge(UNSTARTED)
        repository.updateTrackerEntry(999, mapOf("minutes" to "30"))
        repository.deleteTrackerEntry(999)

        assertNull(progress(UNSTARTED))
        assertEquals(before, progress())
    }

    private companion object {
        const val BADGE = "personal-fitness"
        const val UNSTARTED = "archery"
    }
}
