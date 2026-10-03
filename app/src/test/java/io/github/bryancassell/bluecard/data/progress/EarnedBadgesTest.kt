package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.MeritBadgesNeeded
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EarnedBadgesTest {
    private val version = LocalDate.of(2026, 1, 1)
    private val march = LocalDate.of(2026, 3, 1)
    private val april = LocalDate.of(2026, 4, 1)
    private val may = LocalDate.of(2026, 5, 1)

    // Each badge needs requirement 1.
    private fun badge(id: String, eagleGroup: String? = null, eagleRequired: Boolean = false) =
        MeritBadge(
            id = id,
            name = id.replaceFirstChar { it.titlecase() },
            summary = "Our summary of $id.",
            officialUrl = "https://www.scouting.org/merit-badges/$id/",
            eagleRequired = eagleRequired || eagleGroup != null,
            eagleGroup = eagleGroup,
            requirementVersions = listOf(
                RequirementsVersion(version, listOf(Requirement("1", "Do it.")))
            )
        )

    private val chess = badge("chess")
    private val camping = badge("camping", eagleRequired = true)
    private val cycling = badge("cycling", eagleGroup = "cycling-hiking-swimming")
    private val hiking = badge("hiking", eagleGroup = "cycling-hiking-swimming")
    private val swimming = badge("swimming", eagleGroup = "cycling-hiking-swimming")
    private val catalog = listOf(camping, chess, cycling, hiking, swimming)

    /** [badge] with requirement 1 completed on [on], or with no date. */
    private fun completed(badge: MeritBadge, on: LocalDate?) = badge.id to BadgeProgressDetails(
        BadgeProgress(badge.id, version, version),
        listOf(RequirementProgress(badge.id, "1", completed = true, completedDate = on)),
        emptyList()
    )

    /** [badge] marked completed on [on], without its requirements. */
    private fun marked(badge: MeritBadge, on: LocalDate) = badge.id to BadgeProgressDetails(
        BadgeProgress(badge.id, version, version, completedOnPriorDate = on),
        emptyList(),
        emptyList()
    )

    private fun started(badge: MeritBadge) = badge.id to BadgeProgressDetails(
        BadgeProgress(badge.id, version, version),
        emptyList(),
        emptyList()
    )

    private fun earned(vararg progress: Pair<String, BadgeProgressDetails>) =
        catalog.earnedBadges(progress.toMap())

    @Test
    fun earnedBadges_areTheCompletedOnes_inCatalogOrder() {
        val earned = earned(
            completed(hiking, may),
            started(swimming),
            marked(chess, march),
            completed(camping, null)
        )

        assertEquals(
            listOf(
                EarnedBadge(camping, null, countsOnceAsEagleRequired = true),
                EarnedBadge(chess, march, countsOnceAsEagleRequired = false),
                EarnedBadge(hiking, may, countsOnceAsEagleRequired = true)
            ),
            earned.badges
        )
    }

    // As a rank's requirement that asks for merit badges lists them.
    @Test
    fun inNameOrder_sortsTheBadgesByName() {
        val earned = EarnedBadges(
            listOf(
                EarnedBadge(swimming, may, countsOnceAsEagleRequired = true),
                EarnedBadge(chess, march, countsOnceAsEagleRequired = false),
                EarnedBadge(camping, null, countsOnceAsEagleRequired = true)
            )
        )

        assertEquals(listOf(camping, chess, swimming), earned.inNameOrder().map { it.badge })
    }

    // Ranks' progress is read with the badges'. A rank isn't in the badge catalog, so it's left
    // out.
    @Test
    fun earnedBadges_leaveOutProgressNotInTheCatalog() {
        val rank = completed(badge("star"), march)

        assertEquals(emptyList<EarnedBadge>(), earned(rank).badges)
    }

    @Test
    fun ofAGroup_onlyTheFirstCompletedCountsOnceAsEagleRequired() {
        val earned = earned(
            completed(cycling, may),
            completed(hiking, march),
            completed(swimming, april)
        )

        assertEquals(
            listOf(false, true, false),
            earned.badges.map { it.countsOnceAsEagleRequired }
        )
    }

    @Test
    fun ofAGroup_oneWithADateComesBeforeOneWithout() {
        val earned = earned(completed(cycling, null), completed(hiking, may))

        assertEquals(listOf(false, true), earned.badges.map { it.countsOnceAsEagleRequired })
    }

    @Test
    fun ofAGroup_completedTheSameDay_theFirstInCatalogOrderCounts() {
        val earned = earned(completed(swimming, may), completed(hiking, may))

        assertEquals(listOf(true, false), earned.badges.map { it.countsOnceAsEagleRequired })
    }

    // As for Star 3 and Life 3.
    @Test
    fun toward_countsTheBadges_andEveryEagleRequiredOne() {
        val credit = earned(
            completed(chess, march),
            completed(hiking, march),
            completed(swimming, march)
        ).toward(MeritBadgesNeeded(total = 6, eagleRequired = 4))

        assertEquals(3, credit.completed)
        assertEquals(2, credit.eagleRequired)
        assertNull(credit.completion)
    }

    // As for Eagle 3.
    @Test
    fun toward_whoseGroupsCountOnce_countsOneBadgeOfAGroupAsEagleRequired() {
        val credit = earned(
            completed(chess, march),
            completed(hiking, march),
            completed(swimming, march)
        ).toward(MeritBadgesNeeded(total = 6, eagleRequired = 4, eagleGroupsCountOnce = true))

        assertEquals(3, credit.completed)
        assertEquals(1, credit.eagleRequired)
    }

    @Test
    fun countsAsEagleRequired_followsTheRequirementsRule() {
        val second = EarnedBadge(swimming, may, countsOnceAsEagleRequired = false)
        val elective = EarnedBadge(chess, may, countsOnceAsEagleRequired = false)

        assertEquals(true, second.countsAsEagleRequired(MeritBadgesNeeded(2, 1)))
        assertEquals(false, second.countsAsEagleRequired(MeritBadgesNeeded(2, 1, true)))
        assertEquals(false, elective.countsAsEagleRequired(MeritBadgesNeeded(2, 1)))
    }

    @Test
    fun toward_completesOnTheDayThereWereFirstEnough() {
        val earned = earned(
            completed(chess, march),
            completed(camping, may),
            completed(hiking, april)
        )

        assertEquals(
            Completion(april),
            earned.toward(MeritBadgesNeeded(total = 2, eagleRequired = 1)).completion
        )
        // Enough badges by April, but only one Eagle-required until May.
        assertEquals(
            Completion(may),
            earned.toward(MeritBadgesNeeded(total = 2, eagleRequired = 2)).completion
        )
    }

    @Test
    fun toward_aSecondBadgeOfAGroup_completesIt_onTheDayItWasCompleted() {
        val earned = earned(completed(hiking, march), completed(swimming, april))

        assertEquals(
            Completion(april),
            earned.toward(MeritBadgesNeeded(total = 2, eagleRequired = 2)).completion
        )
    }

    // Only one badge of a group counts as Eagle-required, so a second doesn't complete it.
    @Test
    fun toward_whoseGroupsCountOnce_aSecondBadgeOfAGroup_isntEagleRequired() {
        val earned = earned(completed(hiking, march), completed(swimming, april))

        val credit = earned.toward(MeritBadgesNeeded(2, 2, eagleGroupsCountOnce = true))

        assertEquals(2, credit.completed)
        assertEquals(1, credit.eagleRequired)
        assertNull(credit.completion)
    }

    // The group counts from the day its first badge was completed.
    @Test
    fun toward_whoseGroupsCountOnce_completesOnTheDayThereWereFirstEnough() {
        val earned = earned(
            completed(swimming, april),
            completed(hiking, march),
            completed(camping, may)
        )

        assertEquals(
            Completion(may),
            earned.toward(MeritBadgesNeeded(2, 2, eagleGroupsCountOnce = true)).completion
        )
    }

    @Test
    fun toward_aBadgeWithNoDateNeeded_completesWithNoDate() {
        val earned = earned(completed(chess, march), completed(camping, null))

        assertEquals(
            Completion(null),
            earned.toward(MeritBadgesNeeded(total = 2, eagleRequired = 1)).completion
        )
    }

    @Test
    fun toward_badgesWithDatesEnough_completesOnTheirDay_thoughOneHasNoDate() {
        val earned = earned(
            completed(chess, march),
            completed(camping, april),
            completed(hiking, null)
        )

        assertEquals(
            Completion(april),
            earned.toward(MeritBadgesNeeded(total = 2, eagleRequired = 1)).completion
        )
    }

    @Test
    fun none_givesNoCredit() {
        val credit = EarnedBadges.None.toward(MeritBadgesNeeded(total = 6, eagleRequired = 4))

        assertEquals(MeritBadgeCredit(MeritBadgesNeeded(6, 4), 0, 0, null), credit)
        assertEquals(6, credit.moreNeeded)
        assertEquals(4, credit.moreEagleRequiredNeeded)
        assertEquals(0, credit.counted)
    }

    @Test
    fun moreNeeded_isTheBadgesShort_whenAnyBadgeWillDo() {
        val credit = MeritBadgeCredit(MeritBadgesNeeded(6, 4), 4, 4, null)

        assertEquals(2, credit.moreNeeded)
        assertEquals(0, credit.moreEagleRequiredNeeded)
        assertEquals(4, credit.counted)
    }

    // Seven badges, but only two Eagle-required: two more Eagle-required badges complete it, and
    // only two of the five that aren't count toward the six, with the two Eagle-required ones.
    @Test
    fun moreNeeded_isTheEagleRequiredShort_whenOnlyThoseWillDo() {
        val credit = MeritBadgeCredit(MeritBadgesNeeded(6, 4), 7, 2, null)

        assertEquals(2, credit.moreNeeded)
        assertEquals(2, credit.moreEagleRequiredNeeded)
        assertEquals(4, credit.counted)
    }

    @Test
    fun moreNeeded_someOfThemEagleRequired() {
        val credit = MeritBadgeCredit(MeritBadgesNeeded(6, 4), 3, 3, null)

        assertEquals(3, credit.moreNeeded)
        assertEquals(1, credit.moreEagleRequiredNeeded)
        assertEquals(3, credit.counted)
    }

    @Test
    fun moreNeeded_isNone_withMoreThanEnough() {
        val credit = MeritBadgeCredit(MeritBadgesNeeded(6, 4), 9, 5, Completion(may))

        assertEquals(0, credit.moreNeeded)
        assertEquals(0, credit.moreEagleRequiredNeeded)
        assertEquals(6, credit.counted)
    }
}
