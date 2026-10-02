package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.catalog.Rank
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RankStatusTest {
    private val version = LocalDate.of(2026, 1, 1)
    private val day = LocalDate.of(2026, 4, 15)

    // Each rank needs requirements 1 and 2.
    private fun rank(id: String) = Rank(
        id = id,
        name = id.replaceFirstChar { it.titlecase() },
        summary = "Our summary of $id.",
        officialUrl = "https://www.scouting.org/$id.pdf",
        requirementVersions = listOf(
            RequirementsVersion(
                version,
                listOf(Requirement("1", "First."), Requirement("2", "Second."))
            )
        )
    )

    private val scout = rank("scout")
    private val tenderfoot = rank("tenderfoot")
    private val secondClass = rank("second-class")
    private val firstClass = rank("first-class")
    private val ranks = listOf(scout, tenderfoot, secondClass, firstClass)

    private fun progress(
        rank: Rank,
        vararg done: String,
        startedOn: LocalDate = version,
        earnedOnPriorDate: LocalDate? = null
    ) = rank.id to BadgeProgressDetails(
        BadgeProgress(rank.id, startedOn, day, completedOnPriorDate = earnedOnPriorDate),
        done.map { RequirementProgress(rank.id, it, completed = true, completedDate = day) },
        emptyList()
    )

    private fun statuses(vararg progress: Pair<String, BadgeProgressDetails>) =
        ranks.standings(progress.toMap()).map { it.status }

    @Test
    fun newScout_scoutIsInProgress_withNothingDone_andTheRestHaveNoBar() {
        val standings = ranks.standings(emptyMap())

        assertEquals(ranks, standings.map { it.rank })
        assertEquals(
            listOf(
                RankStanding(scout, RankStatus.InProgress, fractionDone = 0f),
                RankStanding(tenderfoot, RankStatus.NotEarned),
                RankStanding(secondClass, RankStatus.NotEarned),
                RankStanding(firstClass, RankStatus.NotEarned)
            ),
            standings
        )
    }

    @Test
    fun completeRank_isEarned_andTheNextIsInProgress() {
        assertEquals(
            listOf(
                RankStatus.Earned,
                RankStatus.Earned,
                RankStatus.InProgress,
                RankStatus.NotEarned
            ),
            statuses(progress(scout, "1", "2"), progress(tenderfoot, "1", "2"))
        )
    }

    @Test
    fun completeRank_isntEarned_untilTheRankBelowIs() {
        val standings = ranks.standings(mapOf(progress(tenderfoot, "1", "2")))

        assertEquals(RankStatus.InProgress, standings[0].status)
        // All done, but waiting on Scout.
        assertEquals(
            RankStanding(tenderfoot, RankStatus.NotEarned, 1f, waitingOn = scout),
            standings[1]
        )
    }

    @Test
    fun completeRanks_overARankNotEarned_arentEarned() {
        assertEquals(
            listOf(
                RankStatus.Earned,
                RankStatus.InProgress,
                RankStatus.NotEarned,
                RankStatus.NotEarned
            ),
            statuses(
                progress(scout, "1", "2"),
                progress(tenderfoot, "1"),
                progress(secondClass, "1", "2"),
                progress(firstClass, "1", "2")
            )
        )
    }

    @Test
    fun completeRanks_overARankNotEarned_waitOnTheRankBelowEach() {
        val standings = ranks.standings(
            mapOf(
                progress(scout, "1", "2"),
                progress(secondClass, "1", "2"),
                progress(firstClass, "1", "2")
            )
        )

        assertEquals(listOf(null, null, tenderfoot, secondClass), standings.map { it.waitingOn })
    }

    @Test
    fun earnedRank_isEarnedOnTheDateItsLastRequirementWasCompleted() {
        val later = day.plusDays(3)
        val scoutProgress = progress(scout, "1", "2").second.let {
            it.copy(
                requirements = it.requirements.map { done ->
                    if (done.requirementNumber == "2") done.copy(completedDate = later) else done
                }
            )
        }

        assertEquals(later, ranks.standings(mapOf("scout" to scoutProgress))[0].earnedOn)
    }

    @Test
    fun earnedRank_withARequirementCompletedWithNoDate_hasNoDate() {
        val scoutProgress = progress(scout, "1", "2").second.let {
            it.copy(requirements = it.requirements.map { done -> done.copy(completedDate = null) })
        }
        val standing = ranks.standings(mapOf("scout" to scoutProgress))[0]

        assertEquals(RankStatus.Earned, standing.status)
        assertNull(standing.earnedOn)
    }

    // The date it was marked with, not the date its requirements were completed on.
    @Test
    fun markedRank_completeFromItsRequirementsToo_isEarnedOnTheDateItWasMarkedWith() {
        val marked = LocalDate.of(2025, 6, 1)

        assertEquals(
            marked,
            ranks.standings(mapOf(progress(scout, "1", "2", earnedOnPriorDate = marked)))[0]
                .earnedOn
        )
    }

    @Test
    fun startedRank_notEarned_showsHowMuchIsDone_withoutBeingInProgress() {
        val standings = ranks.standings(mapOf(progress(secondClass, "1")))

        assertEquals(RankStatus.InProgress, standings[0].status)
        assertEquals(RankStanding(secondClass, RankStatus.NotEarned, 0.5f), standings[2])
    }

    @Test
    fun startedRank_withNothingRecorded_showsItsBar() {
        assertEquals(0f, ranks.standings(mapOf(progress(firstClass)))[3].fractionDone)
    }

    @Test
    fun inProgressRank_showsHowMuchIsDone() {
        val standings = ranks.standings(mapOf(progress(scout, "1", "2"), progress(tenderfoot, "2")))

        assertEquals(RankStanding(tenderfoot, RankStatus.InProgress, 0.5f), standings[1])
    }

    @Test
    fun earnedRank_hasNoBar() {
        assertNull(ranks.standings(mapOf(progress(scout, "1", "2")))[0].fractionDone)
    }

    @Test
    fun rankMarkedEarned_countsEveryRankBelowAsEarned_withIt() {
        val standings = ranks.standings(mapOf(progress(secondClass, earnedOnPriorDate = day)))

        assertEquals(
            listOf(
                RankStanding(scout, RankStatus.Earned, earnedWith = secondClass),
                RankStanding(tenderfoot, RankStatus.Earned, earnedWith = secondClass),
                RankStanding(secondClass, RankStatus.Earned, earnedOn = day),
                RankStanding(firstClass, RankStatus.InProgress, fractionDone = 0f)
            ),
            standings
        )
    }

    @Test
    fun unmarkingARank_undoesWhatItsMarkCounted() {
        val marked = progress(secondClass, "1", earnedOnPriorDate = day)
        val unmarked = progress(secondClass, "1")

        assertEquals(RankStatus.Earned, statuses(marked)[0])
        assertEquals(
            listOf(
                RankStatus.InProgress,
                RankStatus.NotEarned,
                RankStatus.NotEarned,
                RankStatus.NotEarned
            ),
            statuses(unmarked)
        )
    }

    @Test
    fun rankUnderAMarkedRank_completeFromItsRequirements_isntEarnedWithIt() {
        val standings = ranks.standings(
            mapOf(progress(tenderfoot, "1", "2"), progress(secondClass, earnedOnPriorDate = day))
        )

        assertEquals(secondClass, standings[0].earnedWith)
        assertEquals(RankStanding(tenderfoot, RankStatus.Earned, earnedOn = day), standings[1])
    }

    @Test
    fun rankUnderAMarkedRank_markedItself_isntEarnedWithIt() {
        val standings = ranks.standings(
            mapOf(
                progress(scout, earnedOnPriorDate = day),
                progress(secondClass, earnedOnPriorDate = day)
            )
        )

        assertNull(standings[0].earnedWith)
        assertEquals(secondClass, standings[1].earnedWith)
    }

    @Test
    fun rankUnderTwoMarkedRanks_isEarnedWithTheNearer() {
        val standings = ranks.standings(
            mapOf(
                progress(tenderfoot, earnedOnPriorDate = day),
                progress(firstClass, earnedOnPriorDate = day)
            )
        )

        assertEquals(
            listOf(tenderfoot, null, firstClass, null),
            standings.map { it.earnedWith }
        )
        assertEquals(List(4) { RankStatus.Earned }, standings.map { it.status })
    }

    @Test
    fun versionMissingFromCatalog_isntEarned_andHasNoBar() {
        val standings = ranks.standings(
            mapOf(progress(scout, "1", "2", startedOn = LocalDate.of(2024, 1, 1)))
        )

        assertEquals(RankStanding(scout, RankStatus.InProgress), standings[0])
    }

    @Test
    fun versionMissingFromCatalog_markedEarned_isEarned() {
        val missing = LocalDate.of(2024, 1, 1)
        val standings = ranks.standings(
            mapOf(progress(tenderfoot, startedOn = missing, earnedOnPriorDate = day))
        )

        assertEquals(RankStanding(tenderfoot, RankStatus.Earned, earnedOn = day), standings[1])
        assertEquals(tenderfoot, standings[0].earnedWith)
    }
}
