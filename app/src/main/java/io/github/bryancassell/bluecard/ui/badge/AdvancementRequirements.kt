package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.data.catalog.Advancement
import io.github.bryancassell.bluecard.data.catalog.Rank
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.EarnedBadges
import io.github.bryancassell.bluecard.data.progress.RankStanding
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import io.github.bryancassell.bluecard.data.progress.TrackerEntry
import io.github.bryancassell.bluecard.data.progress.hasEnoughChildren
import io.github.bryancassell.bluecard.data.progress.readsAsMarked
import io.github.bryancassell.bluecard.data.progress.requirementsVersionFor
import io.github.bryancassell.bluecard.data.progress.standings

/**
 * A badge or rank, the requirements the scout works on, and what they've recorded against them.
 */
data class AdvancementRequirements<out A : Advancement>(
    val advancement: A,
    /** The version it's worked on ([requirementsVersionFor]). */
    val version: RequirementsVersion,
    /** The scout's recorded requirement progress, keyed by requirement number. */
    val recorded: Map<String, RequirementProgress>,
    /** The scout's tracker entries, keyed by requirement number. */
    val trackerEntries: Map<String, List<TrackerEntry>>,
    /**
     * Whether the scout marked it completed on a prior date, or, for a rank, marked a rank above
     * it earned, which counts it as earned ([advancementRequirementsAmong]).
     */
    val completedOnPriorDate: Boolean = false,
    /**
     * The badges the scout has completed, which a rank's requirements that ask for merit badges
     * count. None for a badge, whose requirements don't ask for any.
     */
    val earnedBadges: EarnedBadges = EarnedBadges.None
) {
    /** [requirement] of this badge or rank as a row. */
    fun item(requirement: Requirement) = requirement.toItem(
        recorded,
        trackerEntries,
        partOfHasEnough = partOfHasEnough(requirement.number),
        advancementCompletedOnPriorDate = completedOnPriorDate,
        earnedBadges = earnedBadges
    )

    /**
     * Whether the scout has recorded anything for a requirement numbered in [numbers]: its
     * completion, sign-off, comment or tracker entries. That includes a date they gave one that's
     * complete once its tracker's rows are, which doesn't show while a row is deleted but is kept.
     */
    fun hasRecorded(numbers: Collection<String>): Boolean = numbers.any { number ->
        val progress = recorded[number]
        val marked = progress != null &&
            (progress.completed || progress.signedOffBy != null || progress.comment != null)
        marked || number in trackerEntries
    }

    /**
     * Whether a requirement that the one numbered [number] is part of, at any depth, has enough
     * complete sub-requirements ([hasEnoughChildren]).
     */
    private fun partOfHasEnough(number: String): Boolean = version.pathTo(number).orEmpty()
        .dropLast(1)
        .any { it.hasEnoughChildren(recorded, trackerEntries, earnedBadges) }
}

/**
 * Badge or rank [id] from this catalog with the scout's [progress] on it, or null if the
 * catalog doesn't have it or the version it was started on. Only a catalog edited during
 * development can cause that: released catalogs keep every badge, rank and version they shipped,
 * since progress is stored against them.
 */
fun <A : Advancement> List<A>.advancementRequirements(
    id: String,
    progress: BadgeProgressDetails?
): AdvancementRequirements<A>? {
    val advancement = find { it.id == id } ?: return null
    val version = advancement.requirementsVersionFor(progress) ?: return null
    return AdvancementRequirements(
        advancement,
        version,
        progress?.requirements.orEmpty().associateBy { it.requirementNumber },
        progress?.trackerEntries.orEmpty().groupBy { it.requirementNumber },
        progress?.badge?.completedOnPriorDate != null
    )
}

/**
 * As [advancementRequirements], from the scout's [progress] keyed by ID, which for a rank must
 * have every rank's, and the badges they've completed ([earnedBadges]), which a rank's
 * requirements that ask for merit badges count. A rank that counts as earned because the scout
 * marked a rank above it earned on a prior date ([RankStanding.earnedWith]) reads as marked
 * itself, so a requirement not recorded for it reads "Not recorded", as on the rank they marked.
 * Every page that shows a rank's requirements reads them through this, so they agree. A caller
 * that has already worked out the ranks' [standings] from [progress] passes them, so they aren't
 * worked out again.
 */
fun <A : Advancement> List<A>.advancementRequirementsAmong(
    id: String,
    progress: Map<String, BadgeProgressDetails>,
    earnedBadges: EarnedBadges,
    standings: List<RankStanding>? = null
): AdvancementRequirements<A>? {
    val found = advancementRequirements(id, progress[id]) ?: return null
    if (found.advancement !is Rank) return found
    val standing = (standings ?: filterIsInstance<Rank>().standings(progress, earnedBadges))
        .first { it.rank.id == id }
    return found.copy(
        completedOnPriorDate = standing.readsAsMarked(progress[id]),
        earnedBadges = earnedBadges
    )
}

/** The requirement numbered [number], at any depth, or null if there is none. */
fun RequirementsVersion.find(number: String): Requirement? = pathTo(number)?.last()

/** The numbers of this requirement and of every one under it, at any depth. */
fun Requirement.numbersWithin(): List<String> =
    listOf(number) + children.flatMap { it.numbersWithin() }

/**
 * The requirements from the top level down to the one numbered [number], or null if there is
 * none.
 */
private fun RequirementsVersion.pathTo(number: String): List<Requirement>? =
    requirements.firstNotNullOfOrNull { it.pathTo(number) }

/** This requirement down to the one numbered [number], or null if that isn't in it. */
private fun Requirement.pathTo(number: String): List<Requirement>? = if (this.number == number) {
    listOf(this)
} else {
    children.firstNotNullOfOrNull { it.pathTo(number) }?.let { listOf(this) + it }
}
