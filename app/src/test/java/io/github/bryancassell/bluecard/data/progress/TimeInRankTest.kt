package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.catalog.Rank
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeInRankTest {
    private val version = LocalDate.of(2026, 1, 1)
    private val day = LocalDate.of(2026, 4, 15)

    private val active = Requirement("1", "Be active for four months.", monthsInRank = 4)
    private val lead = Requirement("2", "Lead.")

    private fun rank(id: String, name: String) = Rank(
        id = id,
        name = name,
        summary = "Our summary of $name.",
        officialUrl = "https://www.scouting.org/$id.pdf",
        requirementVersions = listOf(RequirementsVersion(version, listOf(active, lead)))
    )

    private val firstClass = rank("first-class", "First Class")
    private val star = rank("star", "Star")
    private val life = rank("life", "Life")
    private val ranks = listOf(firstClass, star, life)

    private fun progress(
        rank: Rank,
        vararg done: String,
        completedDate: LocalDate? = day,
        earnedOnPriorDate: LocalDate? = null
    ) = rank.id to BadgeProgressDetails(
        BadgeProgress(rank.id, version, day, completedOnPriorDate = earnedOnPriorDate),
        done.map {
            RequirementProgress(rank.id, it, completed = true, completedDate = completedDate)
        },
        emptyList()
    )

    private fun starTimeInRank(vararg progress: Pair<String, BadgeProgressDetails>) =
        ranks.standings(progress.toMap()).timeInRank("star", active)

    private fun starEligibility(vararg progress: Pair<String, BadgeProgressDetails>) =
        starTimeInRank(*progress)?.eligibility

    @Test
    fun requirementWithoutMonthsInRank_hasNone() {
        assertNull(ranks.standings(emptyMap()).timeInRank("star", lead))
    }

    @Test
    fun rankBelowNotEarned_saysSo() {
        assertEquals(
            TimeInRank(4, "First Class", TimeInRank.Eligibility.RankBelowNotEarned),
            starTimeInRank(progress(firstClass, "1"))
        )
    }

    @Test
    fun rankBelowMarkedEarned_countsTheMonthsFromItsDate() {
        assertEquals(
            TimeInRank(4, "First Class", TimeInRank.Eligibility.From(LocalDate.of(2026, 9, 1))),
            starTimeInRank(progress(firstClass, earnedOnPriorDate = LocalDate.of(2026, 5, 1)))
        )
    }

    @Test
    fun rankBelowEarnedFromItsRequirements_countsTheMonthsFromWhenTheyWereCompleted() {
        assertEquals(
            TimeInRank.Eligibility.From(LocalDate.of(2026, 8, 15)),
            starEligibility(progress(firstClass, "1", "2"))
        )
    }

    @Test
    fun aDayTheMonthDoesntHave_movesToItsLast() {
        assertEquals(
            TimeInRank.Eligibility.From(LocalDate.of(2027, 2, 28)),
            starEligibility(progress(firstClass, earnedOnPriorDate = LocalDate.of(2026, 10, 31)))
        )
    }

    @Test
    fun rankBelowCountedAsEarnedWithARankAbove_isEarnedWithNoDate() {
        assertEquals(
            TimeInRank.Eligibility.RankBelowHasNoDate,
            starEligibility(progress(life, earnedOnPriorDate = day))
        )
    }

    @Test
    fun rankBelowEarnedFromARequirementCompletedWithNoDate_isEarnedWithNoDate() {
        assertEquals(
            TimeInRank.Eligibility.RankBelowHasNoDate,
            starEligibility(progress(firstClass, "1", "2", completedDate = null))
        )
    }

    // As after Add date on a rank counted as earned with a rank above it.
    @Test
    fun rankBelowGivenItsOwnDate_underAMarkedRank_countsFromThatDate() {
        assertEquals(
            TimeInRank.Eligibility.From(LocalDate.of(2026, 7, 10)),
            starEligibility(
                progress(firstClass, earnedOnPriorDate = LocalDate.of(2026, 3, 10)),
                progress(life, earnedOnPriorDate = day)
            )
        )
    }

    @Test
    fun lowestRank_hasNoneBelowToCountFrom() {
        assertNull(ranks.standings(emptyMap()).timeInRank("first-class", active))
    }
}

/** The scout's standing on each rank when they haven't completed any badges. */
private fun List<Rank>.standings(progress: Map<String, BadgeProgressDetails>) =
    standings(progress, EarnedBadges.None)
