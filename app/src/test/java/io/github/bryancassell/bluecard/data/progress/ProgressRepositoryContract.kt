package io.github.bryancassell.bluecard.data.progress

import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Behavior every [ProgressRepository] must have. Runs against the Room implementation
 * (RoomProgressRepositoryTest) and the fake (FakeProgressRepositoryTest), so the fake that
 * other features' tests use behaves like the real one.
 */
abstract class ProgressRepositoryContract {
    protected abstract val repository: ProgressRepository

    /** A repository whose stored progress can't be read. */
    protected abstract fun unreadableRepository(): ProgressRepository

    /** A repository that can't save progress. */
    protected abstract fun unwritableRepository(): ProgressRepository

    private val version = LocalDate.of(2026, 1, 1)
    private val started = LocalDate.of(2026, 3, 1)
    private val day = LocalDate.of(2026, 4, 15)

    // Recording functions that can start a badge take one; most tests start the badge first.
    private val badgeStart = BadgeStart(version, started)

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
    fun setCounselor_dropsBlankFields_andRemovesAnEmptyCounselor() = test {
        repository.setCounselor(BADGE, Counselor(name = "Pat", phone = " ", email = ""))
        assertEquals(Counselor(name = "Pat"), progress()!!.badge.counselor)

        repository.setCounselor(BADGE, Counselor(name = "  "))
        assertNull(progress()!!.badge.counselor)

        repository.setCounselor(BADGE, Counselor())
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
        repository.markRequirementCompleted(BADGE, "1", day, badgeStart)
        repository.markRequirementCompleted(BADGE, "2", null, badgeStart)

        assertEquals(
            RequirementProgress(BADGE, "1", completed = true, completedDate = day),
            requirement("1")
        )
        assertEquals(RequirementProgress(BADGE, "2", completed = true), requirement("2"))
    }

    @Test
    fun markRequirementNotCompleted_returnsTheProgressFromBefore() = test {
        repository.markRequirementCompleted(BADGE, "1", day, badgeStart)

        assertEquals(
            RequirementProgress(BADGE, "1", completed = true, completedDate = day),
            repository.markRequirementNotCompleted(BADGE, "1")
        )
        // Unchecked again, as by a quick double tap.
        assertEquals(
            RequirementProgress(BADGE, "1"),
            repository.markRequirementNotCompleted(BADGE, "1")
        )
        assertNull(repository.markRequirementNotCompleted(BADGE, "2"))
    }

    @Test
    fun markRequirementNotCompleted_removesDateButKeepsComment() = test {
        repository.setRequirementComment(BADGE, "1", "Hiked with my troop.", badgeStart)
        repository.markRequirementCompleted(BADGE, "1", day, badgeStart)

        repository.markRequirementNotCompleted(BADGE, "1")

        assertEquals(
            RequirementProgress(BADGE, "1", comment = "Hiked with my troop."),
            requirement("1")
        )
    }

    @Test
    fun setRequirementCompletedDate_changesAndRemovesDateOfCompletedRequirement() = test {
        repository.markRequirementCompleted(BADGE, "1", day, badgeStart)

        repository.setRequirementCompletedDate(BADGE, "1", started)
        assertEquals(RequirementProgress(BADGE, "1", true, started), requirement("1"))

        repository.setRequirementCompletedDate(BADGE, "1", null)
        assertEquals(RequirementProgress(BADGE, "1", completed = true), requirement("1"))
    }

    @Test
    fun setRequirementCompletedDate_doesNothingUnlessCompleted() = test {
        repository.setRequirementComment(BADGE, "1", "Not done yet.", badgeStart)
        val before = progress()

        repository.setRequirementCompletedDate(BADGE, "1", day)
        repository.setRequirementCompletedDate(BADGE, "2", day)
        repository.setRequirementCompletedDate(UNSTARTED, "1", day)

        assertEquals(before, progress())
        assertNull(progress(UNSTARTED))
    }

    @Test
    fun setRequirementComment_trimsSpacesAroundIt() = test {
        repository.setRequirementComment(BADGE, "1", "  Done at camp.\n", badgeStart)

        assertEquals("Done at camp.", requirement("1")?.comment)
    }

    @Test
    fun setRequirementComment_keepsCompletion_andBlankRemovesIt() = test {
        repository.markRequirementCompleted(BADGE, "1", day, badgeStart)

        repository.setRequirementComment(BADGE, "1", "Done at camp.", badgeStart)
        assertEquals(RequirementProgress(BADGE, "1", true, day, "Done at camp."), requirement("1"))

        repository.setRequirementComment(BADGE, "1", "  ", badgeStart)
        assertEquals(RequirementProgress(BADGE, "1", true, day), requirement("1"))
    }

    @Test
    fun recordingWithStart_startsAnUnstartedBadge() = test {
        repository.markRequirementCompleted(UNSTARTED, "1", day, BadgeStart(version, day))
        repository.setRequirementComment(OTHER, "1", "Next week.", BadgeStart(version, day))

        assertEquals(BadgeProgress(UNSTARTED, version, day), progress(UNSTARTED)!!.badge)
        assertEquals(
            listOf(RequirementProgress(UNSTARTED, "1", completed = true, completedDate = day)),
            progress(UNSTARTED)!!.requirements
        )
        assertEquals(BadgeProgress(OTHER, version, day), progress(OTHER)!!.badge)
        assertEquals(
            listOf(RequirementProgress(OTHER, "1", comment = "Next week.")),
            progress(OTHER)!!.requirements
        )
    }

    @Test
    fun recordingWithStart_onAStartedBadge_keepsItsVersionAndDate() = test {
        val later = BadgeStart(LocalDate.of(2027, 1, 1), day)

        repository.markRequirementCompleted(BADGE, "1", day, later)
        repository.setRequirementComment(BADGE, "2", "Hi", later)

        assertEquals(BadgeProgress(BADGE, version, started), progress()!!.badge)
        assertEquals(listOf("1", "2"), progress()!!.requirements.map { it.requirementNumber })
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
        repository.markRequirementCompleted(BADGE, "1", day, badgeStart)
        repository.setRequirementComment(BADGE, "7a", "Week 1 went well.", badgeStart)
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
        repository.markRequirementCompleted(BADGE, "1", day, badgeStart)
        repository.addTrackerEntry(BADGE, "7a", mapOf("minutes" to "30"))
        repository.markRequirementCompleted("archery", "1", day, badgeStart)

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
        repository.markRequirementCompleted(BADGE, "1", day, badgeStart)
        repository.addTrackerEntry("archery", "2", mapOf("score" to "250"))

        repository.clearAll()

        assertEquals(emptyList<BadgeProgressDetails>(), repository.observeAllProgress().first())
    }

    @Test
    fun observeProgress_emitsWhenProgressChanges() = test {
        val flow = repository.observeProgress(BADGE)
        flow.first { it != null }

        repository.markRequirementCompleted(BADGE, "1", day, badgeStart)

        flow.first { details -> details!!.requirements.any { it.completed } }
    }

    @Test
    fun recordingOnAnUnstartedBadge_failsAndRecordsNothing() = test {
        val writes: List<Pair<String, suspend () -> Unit>> = listOf(
            "setCounselor" to { repository.setCounselor(UNSTARTED, Counselor(name = "Pat")) },
            "setCompletedOnPriorDate" to { repository.setCompletedOnPriorDate(UNSTARTED, day) },
            "markRequirementNotCompleted" to {
                repository.markRequirementNotCompleted(UNSTARTED, "1")
            },
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
        repository.markRequirementCompleted(BADGE, "1", day, badgeStart)
        val before = progress()

        repository.clearRequirement(UNSTARTED, "1")
        repository.clearBadge(UNSTARTED)
        repository.updateTrackerEntry(999, mapOf("minutes" to "30"))
        repository.deleteTrackerEntry(999)

        assertNull(progress(UNSTARTED))
        assertEquals(before, progress())
    }

    @Test
    fun observing_whenUnreadable_throwsIOException() = runTest {
        val unreadable = unreadableRepository()
        val flows = listOf(
            "observeAllProgress" to unreadable.observeAllProgress(),
            "observeProgress" to unreadable.observeProgress(BADGE)
        )
        for ((name, flow) in flows) {
            val error = runCatching { flow.first() }.exceptionOrNull()

            assertTrue("$name should throw an IOException, got $error", error is IOException)
        }
    }

    @Test
    fun writing_whenUnwritable_throwsIOException() = runTest {
        val unwritable = unwritableRepository()
        val writes: List<Pair<String, suspend () -> Unit>> = listOf(
            "startBadge" to { unwritable.startBadge(BADGE, version, started) },
            "setCounselor" to { unwritable.setCounselor(BADGE, Counselor(name = "Pat")) },
            "setCompletedOnPriorDate" to { unwritable.setCompletedOnPriorDate(BADGE, day) },
            "markRequirementCompleted" to
                { unwritable.markRequirementCompleted(BADGE, "1", day, badgeStart) },
            "markRequirementNotCompleted" to { unwritable.markRequirementNotCompleted(BADGE, "1") },
            "setRequirementCompletedDate" to {
                unwritable.setRequirementCompletedDate(BADGE, "1", day)
            },
            "setRequirementComment" to
                { unwritable.setRequirementComment(BADGE, "1", "Hi", badgeStart) },
            "addTrackerEntry" to {
                unwritable.addTrackerEntry(BADGE, "7a", mapOf("minutes" to "30"))
            },
            "updateTrackerEntry" to { unwritable.updateTrackerEntry(1, mapOf("minutes" to "30")) },
            "deleteTrackerEntry" to { unwritable.deleteTrackerEntry(1) },
            "clearRequirement" to { unwritable.clearRequirement(BADGE, "1") },
            "clearBadge" to { unwritable.clearBadge(BADGE) },
            "clearAll" to { unwritable.clearAll() }
        )
        for ((name, write) in writes) {
            val error = runCatching { write() }.exceptionOrNull()

            assertTrue("$name should throw an IOException, got $error", error is IOException)
        }
    }

    private companion object {
        const val BADGE = "personal-fitness"
        const val UNSTARTED = "archery"
        const val OTHER = "camping"
    }
}
