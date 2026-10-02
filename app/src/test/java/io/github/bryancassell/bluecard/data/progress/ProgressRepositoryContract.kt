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
    private val laterDay = LocalDate.of(2026, 4, 18)

    // Recording functions that can start a badge take one; most tests start the badge first.
    private val badgeStart = BadgeStart(version, started)

    private suspend fun progress(badgeId: String = BADGE) =
        repository.observeProgress(badgeId).first()

    private suspend fun requirement(number: String) =
        progress()?.requirements?.singleOrNull { it.requirementNumber == number }

    private suspend fun trackerValues(number: String) = progress()!!.trackerEntries
        .filter { it.requirementNumber == number }.sortedBy { it.id }.map { it.values }

    /** Adds an entry to a log, a tracker without a fixed number of rows. */
    private suspend fun addLogEntry(
        number: String,
        values: Map<String, String>,
        badgeId: String = BADGE,
        addedDate: LocalDate = day
    ) = repository.addTrackerEntry(badgeId, number, null, values, addedDate, badgeStart)

    /** Saves row [rowNumber] of a tracker with a fixed number of rows. */
    private suspend fun saveRow(
        number: String,
        rowNumber: Int,
        values: Map<String, String>,
        addedDate: LocalDate = day
    ) = repository.addTrackerEntry(BADGE, number, rowNumber, values, addedDate, badgeStart)

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
        repository.setCounselor(BADGE, Counselor(name = "Pat"), badgeStart)
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
        repository.setCounselor(
            BADGE,
            Counselor("Pat Lee", "555-0100", "pat@example.com"),
            badgeStart
        )
        assertEquals(
            Counselor("Pat Lee", "555-0100", "pat@example.com"),
            progress()!!.badge.counselor
        )

        repository.setCounselor(BADGE, Counselor(name = "Sam"), badgeStart)
        assertEquals(Counselor(name = "Sam"), progress()!!.badge.counselor)

        repository.setCounselor(BADGE, null, badgeStart)
        assertNull(progress()!!.badge.counselor)
    }

    @Test
    fun setCounselor_trimsSpacesAroundEachField() = test {
        repository.setCounselor(
            BADGE,
            Counselor(" Pat Lee ", "\t555-0100", "pat@example.com\n"),
            badgeStart
        )

        assertEquals(
            Counselor("Pat Lee", "555-0100", "pat@example.com"),
            progress()!!.badge.counselor
        )
    }

    @Test
    fun setCounselor_dropsBlankFields_andRemovesAnEmptyCounselor() = test {
        repository.setCounselor(BADGE, Counselor(name = "Pat", phone = " ", email = ""), badgeStart)
        assertEquals(Counselor(name = "Pat"), progress()!!.badge.counselor)

        repository.setCounselor(BADGE, Counselor(name = "  "), badgeStart)
        assertNull(progress()!!.badge.counselor)

        repository.setCounselor(BADGE, Counselor(), badgeStart)
        assertNull(progress()!!.badge.counselor)
    }

    @Test
    fun setCompletedOnPriorDate_setsChangesAndRemoves() = test {
        repository.markRequirementCompleted(BADGE, "1", day, badgeStart)

        repository.setCompletedOnPriorDate(BADGE, day, badgeStart)
        assertEquals(day, progress()!!.badge.completedOnPriorDate)

        repository.setCompletedOnPriorDate(BADGE, laterDay, badgeStart)
        assertEquals(laterDay, progress()!!.badge.completedOnPriorDate)

        repository.removeCompletedOnPriorDate(BADGE)
        assertEquals(BadgeProgress(BADGE, version, started), progress()!!.badge)
        // What's recorded for its requirements stays.
        assertEquals(listOf("1"), progress()!!.requirements.map { it.requirementNumber })
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
    fun setCompletedFromRowsDate_marksItOnTheDateOrNone_andKeepsTheComment() = test {
        repository.setRequirementComment(BADGE, "2a", "Weekly.", badgeStart)
        saveRow("2a", 1, mapOf("income" to "10"))
        saveRow("2a", 2, mapOf("income" to "20"))

        repository.setCompletedFromRowsDate(BADGE, "2a", rowCount = 2, started)
        assertEquals(RequirementProgress(BADGE, "2a", true, started, "Weekly."), requirement("2a"))

        repository.setCompletedFromRowsDate(BADGE, "2a", rowCount = 2, null)
        assertEquals(
            RequirementProgress(BADGE, "2a", completed = true, comment = "Weekly."),
            requirement("2a")
        )
    }

    @Test
    fun setCompletedFromRowsDate_doesNothingUnlessEveryRowIsFilledIn() = test {
        // Another requirement's row, and a row outside the tracker, fill none of its rows.
        saveRow("2a", 1, mapOf("income" to "10"))
        saveRow("2a", 3, mapOf("income" to "30"))
        saveRow("2c", 2, mapOf("income" to "20"))
        val before = progress()

        repository.setCompletedFromRowsDate(BADGE, "2a", rowCount = 2, day)
        repository.setCompletedFromRowsDate(UNSTARTED, "2a", rowCount = 2, day)

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
        repository.addTrackerEntry(
            FOURTH,
            "4",
            null,
            mapOf("miles" to "10"),
            day,
            BadgeStart(version, day)
        )
        repository.setCounselor(THIRD, Counselor(name = "Pat"), BadgeStart(version, day))

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
        assertEquals(BadgeProgress(FOURTH, version, day), progress(FOURTH)!!.badge)
        assertEquals(
            listOf(mapOf("miles" to "10")),
            progress(FOURTH)!!.trackerEntries.map { it.values }
        )
        assertEquals(
            BadgeProgressDetails(
                BadgeProgress(THIRD, version, day, counselor = Counselor(name = "Pat")),
                emptyList(),
                emptyList()
            ),
            progress(THIRD)
        )
    }

    @Test
    fun recordingWithStart_onAStartedBadge_keepsItsVersionAndDate() = test {
        val later = BadgeStart(LocalDate.of(2027, 1, 1), day)

        repository.markRequirementCompleted(BADGE, "1", day, later)
        repository.setRequirementComment(BADGE, "2", "Hi", later)
        repository.addTrackerEntry(BADGE, "7a", null, mapOf("minutes" to "30"), day, later)
        repository.setCounselor(BADGE, Counselor(name = "Pat"), later)
        repository.setCompletedOnPriorDate(BADGE, day, later)

        assertEquals(
            BadgeProgress(BADGE, version, started, Counselor(name = "Pat"), day),
            progress()!!.badge
        )
        assertEquals(listOf("1", "2"), progress()!!.requirements.map { it.requirementNumber })
        assertEquals(listOf(mapOf("minutes" to "30")), trackerValues("7a"))
    }

    @Test
    fun trackerEntries_addUpdateDelete() = test {
        val first = addLogEntry("7a", mapOf("minutes" to "30"))
        val second = addLogEntry("7a", mapOf("minutes" to "45"))
        assertEquals(
            listOf(mapOf("minutes" to "30"), mapOf("minutes" to "45")),
            trackerValues("7a")
        )

        val changed = mapOf("minutes" to "35", "notes" to "Ran a mile")
        assertEquals(
            first,
            repository.addTrackerEntry(BADGE, "7a", null, changed, day, badgeStart, id = first)
        )
        repository.deleteTrackerEntry(second)

        assertEquals(listOf(changed), trackerValues("7a"))
    }

    @Test
    fun addTrackerEntry_withADeletedEntrysId_addsItAgain() = test {
        val id = addLogEntry("7a", mapOf("minutes" to "30"))
        repository.deleteTrackerEntry(id)

        val added = repository.addTrackerEntry(
            BADGE,
            "7a",
            null,
            mapOf("minutes" to "35"),
            laterDay,
            badgeStart,
            id = id
        )

        assertTrue("$added should be higher than $id", added > id)
        assertEquals(listOf(mapOf("minutes" to "35")), trackerValues("7a"))
        // It's a new row, first saved now.
        assertEquals(listOf(laterDay), progress()!!.trackerEntries.map { it.addedDate })
    }

    @Test
    fun addTrackerEntry_recordsTheDateTheRowWasAdded_andChangingItKeepsIt() = test {
        val session = addLogEntry("7a", mapOf("minutes" to "30"))
        val week1 = saveRow("2", 1, mapOf("income" to "10"))

        // Changed by its entry's ID, and by its row.
        val changed = mapOf("minutes" to "35")
        repository.addTrackerEntry(BADGE, "7a", null, changed, laterDay, badgeStart, id = session)
        saveRow("2", 1, mapOf("income" to "15"), addedDate = laterDay)
        val week2 = saveRow("2", 2, mapOf("income" to "20"), addedDate = laterDay)

        assertEquals(
            mapOf(session to day, week1 to day, week2 to laterDay),
            progress()!!.trackerEntries.associate { it.id to it.addedDate }
        )
    }

    @Test
    fun addTrackerEntry_afterDeletingTheNewest_getsAHigherId() = test {
        addLogEntry("7a", mapOf("minutes" to "30"))
        val newest = addLogEntry("7a", mapOf("minutes" to "45"))
        repository.deleteTrackerEntry(newest)

        val added = addLogEntry("7a", mapOf("minutes" to "50"))

        assertTrue("$added should be higher than $newest", added > newest)
    }

    @Test
    fun addTrackerEntry_toAFilledRow_givesItTheNewValues() = test {
        val week1 = saveRow("2", 1, mapOf("income" to "10"))
        saveRow("2", 2, mapOf("income" to "20"))
        saveRow("3", 1, mapOf("income" to "30"))

        val again = saveRow("2", 1, mapOf("spent" to "5"))

        assertEquals(week1, again)
        assertEquals(
            listOf(
                Triple("2", 1, mapOf("spent" to "5")),
                Triple("2", 2, mapOf("income" to "20")),
                Triple("3", 1, mapOf("income" to "30"))
            ),
            progress()!!.trackerEntries
                .map { Triple(it.requirementNumber, it.rowNumber, it.values) }
                .sortedWith(compareBy({ it.first }, { it.second }))
        )
    }

    @Test
    fun trackerValues_areTrimmed_andBlankOnesDropped() = test {
        val id = addLogEntry("7a", mapOf("activity" to "  Run ", "minutes" to " ", "notes" to ""))
        assertEquals(listOf(mapOf("activity" to "Run")), trackerValues("7a"))

        val changed = mapOf("activity" to "", "minutes" to " 30 ")
        repository.addTrackerEntry(BADGE, "7a", null, changed, day, badgeStart, id = id)
        assertEquals(listOf(mapOf("minutes" to "30")), trackerValues("7a"))
    }

    @Test
    fun clearRequirements_removesOnlyThoseRequirements_andTheBadgeStaysStarted() = test {
        repository.markRequirementCompleted(BADGE, "1", day, badgeStart)
        repository.setRequirementComment(BADGE, "7", "Picked 7a and 7b.", badgeStart)
        repository.setRequirementComment(BADGE, "7a", "Week 1 went well.", badgeStart)
        addLogEntry("7a", mapOf("minutes" to "30"))
        addLogEntry("7b", mapOf("mile" to "9:30"))
        addLogEntry("8", mapOf("pushups" to "20"))

        repository.clearRequirements(BADGE, listOf("7", "7a", "7b"))

        assertNull(requirement("7"))
        assertNull(requirement("7a"))
        assertEquals(emptyList<Map<String, String>>(), trackerValues("7a"))
        assertEquals(emptyList<Map<String, String>>(), trackerValues("7b"))
        assertEquals(
            RequirementProgress(BADGE, "1", completed = true, completedDate = day),
            requirement("1")
        )
        assertEquals(listOf(mapOf("pushups" to "20")), trackerValues("8"))
        assertEquals(BadgeProgress(BADGE, version, started), progress()!!.badge)
    }

    @Test
    fun clearBadge_removesEverythingForThatBadgeOnly() = test {
        repository.startBadge("archery", version, started)
        repository.setCounselor(BADGE, Counselor(name = "Pat"), badgeStart)
        repository.markRequirementCompleted(BADGE, "1", day, badgeStart)
        addLogEntry("7a", mapOf("minutes" to "30"))
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
        addLogEntry("2", mapOf("score" to "250"), badgeId = "archery")

        repository.clearAll()

        assertEquals(emptyList<BadgeProgressDetails>(), repository.observeAllProgress().first())
    }

    @Test
    fun replaceAll_replacesAllProgressWithWhatItsGiven() = test {
        repository.startBadge("archery", version, started)
        repository.markRequirementCompleted(BADGE, "1", day, badgeStart)
        addLogEntry("7a", mapOf("minutes" to "30"))
        val camping = BadgeProgressDetails(
            BadgeProgress("camping", version, started, Counselor(name = "Pat", phone = "555")),
            listOf(
                RequirementProgress("camping", "4b", completed = true, completedDate = day),
                RequirementProgress("camping", "5", comment = "Next trip")
            ),
            listOf(
                TrackerEntry(
                    badgeId = "camping",
                    requirementNumber = "9a",
                    rowNumber = 2,
                    values = mapOf("nights" to "2"),
                    addedDate = laterDay
                ),
                TrackerEntry(badgeId = "camping", requirementNumber = "9b", values = mapOf())
            )
        )
        val swimming = BadgeProgressDetails(
            BadgeProgress("swimming", version, started, completedOnPriorDate = laterDay),
            emptyList(),
            emptyList()
        )

        repository.replaceAll(listOf(camping, swimming))

        // Without the tracker entries' new IDs, and with the requirements in a set order.
        val stored = repository.observeAllProgress().first().map { details ->
            details.copy(
                requirements = details.requirements.sortedBy { it.requirementNumber },
                trackerEntries = details.trackerEntries.sortedBy { it.id }.map { it.copy(id = 0) }
            )
        }
        assertEquals(listOf(camping, swimming), stored)
    }

    @Test
    fun replaceAll_givesTrackerEntriesNewIds_inTheOrderTheyreListed() = test {
        val earlier = addLogEntry("7a", mapOf("minutes" to "30"))
        // IDs that would put the log out of order, and clash with the entry already there.
        val log = listOf(earlier + 2 to "first", earlier + 1 to "second", earlier to "third")
            .map { (id, note) -> TrackerEntry(id, BADGE, "7a", values = mapOf("note" to note)) }

        repository.replaceAll(
            listOf(BadgeProgressDetails(BadgeProgress(BADGE, version, started), emptyList(), log))
        )

        val entries = progress()!!.trackerEntries.sortedBy { it.id }
        assertEquals(listOf("first", "second", "third"), entries.map { it.values["note"] })
        assertTrue(entries.all { it.id > earlier })
    }

    @Test
    fun observeProgress_emitsWhenProgressChanges() = test {
        val flow = repository.observeProgress(BADGE)
        flow.first { it != null }

        repository.markRequirementCompleted(BADGE, "1", day, badgeStart)

        flow.first { details -> details!!.requirements.any { it.completed } }
    }

    @Test
    fun setCompletedOnPriorDate_onAnUnstartedBadge_startsIt() = test {
        repository.setCompletedOnPriorDate(UNSTARTED, day, BadgeStart(version, laterDay))

        assertEquals(
            BadgeProgressDetails(
                BadgeProgress(UNSTARTED, version, laterDay, completedOnPriorDate = day),
                emptyList(),
                emptyList()
            ),
            progress(UNSTARTED)
        )
    }

    @Test
    fun clearingOrChangingSomethingMissing_doesNothing() = test {
        repository.markRequirementCompleted(BADGE, "1", day, badgeStart)
        val before = progress()

        repository.clearRequirements(UNSTARTED, listOf("1"))
        repository.clearBadge(UNSTARTED)
        repository.deleteTrackerEntry(999)
        // As when the scout unchecks a requirement, or unmarks a badge completed on a prior
        // date, on a page shown before its badge was cleared.
        val unchecked = repository.markRequirementNotCompleted(UNSTARTED, "1")
        repository.removeCompletedOnPriorDate(UNSTARTED)

        assertNull(unchecked)
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
            "setCounselor" to
                { unwritable.setCounselor(BADGE, Counselor(name = "Pat"), badgeStart) },
            "setCompletedOnPriorDate" to
                { unwritable.setCompletedOnPriorDate(BADGE, day, badgeStart) },
            "removeCompletedOnPriorDate" to { unwritable.removeCompletedOnPriorDate(BADGE) },
            "markRequirementCompleted" to
                { unwritable.markRequirementCompleted(BADGE, "1", day, badgeStart) },
            "markRequirementNotCompleted" to { unwritable.markRequirementNotCompleted(BADGE, "1") },
            "setRequirementCompletedDate" to {
                unwritable.setRequirementCompletedDate(BADGE, "1", day)
            },
            "setCompletedFromRowsDate" to {
                unwritable.setCompletedFromRowsDate(BADGE, "2a", rowCount = 2, day)
            },
            "setRequirementComment" to
                { unwritable.setRequirementComment(BADGE, "1", "Hi", badgeStart) },
            "addTrackerEntry" to {
                val values = mapOf("minutes" to "30")
                unwritable.addTrackerEntry(BADGE, "7a", null, values, day, badgeStart)
            },
            "deleteTrackerEntry" to { unwritable.deleteTrackerEntry(1) },
            "clearRequirements" to { unwritable.clearRequirements(BADGE, listOf("1")) },
            "clearBadge" to { unwritable.clearBadge(BADGE) },
            "clearAll" to { unwritable.clearAll() },
            "replaceAll" to { unwritable.replaceAll(emptyList()) }
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
        const val THIRD = "cooking"
        const val FOURTH = "cycling"
    }
}
