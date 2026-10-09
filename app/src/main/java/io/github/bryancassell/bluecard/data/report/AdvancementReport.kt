package io.github.bryancassell.bluecard.data.report

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.Completion
import io.github.bryancassell.bluecard.data.progress.Counselor
import io.github.bryancassell.bluecard.data.progress.EarnedBadge
import io.github.bryancassell.bluecard.data.progress.EarnedBadges
import io.github.bryancassell.bluecard.data.progress.MeritBadgeCredit
import io.github.bryancassell.bluecard.data.progress.RankStanding
import io.github.bryancassell.bluecard.data.progress.RankStatus
import io.github.bryancassell.bluecard.data.progress.TrackerEntry
import io.github.bryancassell.bluecard.data.progress.TrackerTotal
import io.github.bryancassell.bluecard.data.progress.columnValues
import io.github.bryancassell.bluecard.data.progress.completion
import io.github.bryancassell.bluecard.data.progress.hasEnoughChildren
import io.github.bryancassell.bluecard.data.progress.hasPartDone
import io.github.bryancassell.bluecard.data.progress.numberedRows
import io.github.bryancassell.bluecard.data.progress.readsAsMarked
import io.github.bryancassell.bluecard.data.progress.requirementsVersionFor
import io.github.bryancassell.bluecard.data.progress.totals
import java.time.LocalDate

/** What a report is on, which its title and file name say. */
enum class ReportKind { MeritBadge, Rank }

/**
 * Everything a badge's or rank's report shows: the scout, the badge or rank, and all they
 * recorded for it.
 */
data class AdvancementReport(
    val profile: Profile,
    val kind: ReportKind,
    /** The badge's or rank's name. */
    val name: String,
    /** Effective date of the requirements version it's worked on. */
    val requirementsVersion: LocalDate,
    /**
     * When the badge was completed or the rank earned, or null for a badge that isn't complete
     * or a rank counted as earned with a rank above it ([earnedWith]).
     */
    val completion: Completion?,
    /**
     * For a rank that counts as earned only because the scout marked a rank above it earned on
     * a prior date ([RankStanding.earnedWith]), that rank's name. Null otherwise.
     */
    val earnedWith: String?,
    /** The badge's counselor, or null if the scout entered none. A rank has none. */
    val counselor: Counselor?,
    /**
     * Every requirement of the version, in catalog order, with its sub-requirements, including
     * those not completed or not needed, so a counselor reading it sees the whole badge.
     */
    val requirements: List<ReportRequirement>,
    /** The day the report was created, which tells an old copy from a new one. */
    val createdDate: LocalDate
)

/**
 * A requirement in a report, with what the scout recorded for it. A time in rank requirement's
 * eligibility date isn't in it: that's only a guide, and the requirement's own date says when it
 * was done.
 */
data class ReportRequirement(
    val number: String,
    val summary: String,
    /** How many of its sub-requirements are needed, when that's fewer than all of them. */
    val requiredCount: Int?,
    /** When it was completed, or null if it isn't complete. */
    val completion: Completion?,
    /**
     * Whether it's no longer needed: it isn't complete, but a requirement it's part of has enough
     * complete sub-requirements, such as a choice the scout didn't pick once enough others are
     * complete. The screens say so too.
     */
    val notNeeded: Boolean,
    /**
     * Whether nothing toward it was recorded: it's still needed and no part of it is done, but
     * the scout marked the badge or rank completed on a prior date, without recording its
     * requirements. The screens say so too.
     */
    val notRecorded: Boolean,
    /** For a rank's requirement, who signed off on it, or null. */
    val signedOffBy: String?,
    val comment: String?,
    /** Its tracker, or null if it has none. */
    val tracker: ReportTracker?,
    /** For a rank's requirement that asks for merit badges, the badges toward it, or null. */
    val meritBadges: ReportMeritBadges?,
    val children: List<ReportRequirement>
)

/** A requirement's tracker, with the rows the scout filled in. */
data class ReportTracker(
    val definition: TrackerDefinition,
    /** The rows filled in, in order: by row number, or in a log in the order they were added. */
    val rows: List<ReportTrackerRow>,
    /** Its columns that have a total ([totals]), in column order. */
    val totals: List<TrackerTotal> = emptyList()
)

/** A filled-in tracker row. */
data class ReportTrackerRow(
    /** The row it fills, from 1, or its place in a log. */
    val number: Int,
    /** Its values as stored, in column order, without the columns it has none for. */
    val values: List<Pair<TrackerColumn, String>>
)

/**
 * A rank's requirement that asks for merit badges ([Requirement.meritBadges]): how far the
 * scout's completed badges go toward it, and every badge they've completed, in name order
 * ([EarnedBadges.inNameOrder]), as its page lists them. So a report made after a later rank lists
 * that rank's badges too, and their dates tell them apart.
 */
data class ReportMeritBadges(val credit: MeritBadgeCredit, val badges: List<EarnedBadge>)

/**
 * The report on this badge for [profile], from their [progress] on it, created on
 * [createdDate]. Null if the catalog doesn't have the version the badge was started on, which
 * only a catalog edited during development can cause.
 */
fun MeritBadge.report(
    profile: Profile,
    progress: BadgeProgressDetails,
    createdDate: LocalDate
): AdvancementReport? {
    val version = requirementsVersionFor(progress) ?: return null
    return AdvancementReport(
        profile = profile,
        kind = ReportKind.MeritBadge,
        name = name,
        requirementsVersion = version.effectiveDate,
        completion = progress.completion(version),
        earnedWith = null,
        counselor = progress.badge.counselor,
        requirements = version.reportRequirements(
            progress,
            completedOnPriorDate = progress.badge.completedOnPriorDate != null,
            earnedBadges = EarnedBadges.None
        ),
        createdDate = createdDate
    )
}

/**
 * The report on this standing's rank, which must be earned, for [profile], from their
 * [progress] on it, null if it isn't started, and the badges they've completed
 * ([earnedBadges]), which its requirements that ask for merit badges count. Created on
 * [createdDate]. Null if the catalog doesn't have the version the rank was started on, which
 * only a catalog edited during development can cause.
 *
 * A rank counted as earned with a rank above it reads as marked itself, as on its page, so a
 * requirement not recorded for it is [ReportRequirement.notRecorded].
 */
fun RankStanding.report(
    profile: Profile,
    progress: BadgeProgressDetails?,
    earnedBadges: EarnedBadges,
    createdDate: LocalDate
): AdvancementReport? {
    require(status == RankStatus.Earned) { "${rank.id} isn't earned, so it has no report" }
    val version = rank.requirementsVersionFor(progress) ?: return null
    return AdvancementReport(
        profile = profile,
        kind = ReportKind.Rank,
        name = rank.name,
        requirementsVersion = version.effectiveDate,
        completion = if (earnedWith == null) Completion(earnedOn) else null,
        earnedWith = earnedWith?.name,
        counselor = null,
        requirements = version.reportRequirements(
            progress,
            completedOnPriorDate = readsAsMarked(progress),
            earnedBadges = earnedBadges
        ),
        createdDate = createdDate
    )
}

/**
 * Every requirement of this version, at every level, with what the scout recorded for it in
 * [progress], null for a rank not started. [completedOnPriorDate] is whether the badge or rank
 * reads as marked completed on a prior date. [earnedBadges] are the badges the scout has
 * completed, which a rank's requirement that asks for merit badges counts.
 */
private fun RequirementsVersion.reportRequirements(
    progress: BadgeProgressDetails?,
    completedOnPriorDate: Boolean,
    earnedBadges: EarnedBadges
): List<ReportRequirement> {
    val recorded = progress?.requirements.orEmpty().associateBy { it.requirementNumber }
    val entries = progress?.trackerEntries.orEmpty().groupBy { it.requirementNumber }
    val badgesByName = earnedBadges.inNameOrder()

    // [partOfHasEnough] is whether a requirement this one is part of, at any depth, has enough
    // complete sub-requirements ([hasEnoughChildren]). Its states are worked out in the same
    // order as in the screens' `toItem`, which works out the same ones.
    fun Requirement.toReport(partOfHasEnough: Boolean): ReportRequirement {
        val completion = completion(recorded, entries, earnedBadges)
        val stillNeeded = !partOfHasEnough && completion == null
        return ReportRequirement(
            number = number,
            summary = summary,
            requiredCount = choiceCount,
            completion = completion,
            notNeeded = partOfHasEnough && completion == null,
            notRecorded = completedOnPriorDate &&
                stillNeeded &&
                !hasPartDone(recorded, entries, earnedBadges),
            signedOffBy = recorded[number]?.signedOffBy,
            comment = recorded[number]?.comment,
            tracker = tracker?.toReport(entries[number].orEmpty()),
            meritBadges = meritBadges?.let {
                ReportMeritBadges(earnedBadges.toward(it), badgesByName)
            },
            children = children.map {
                it.toReport(
                    partOfHasEnough || hasEnoughChildren(recorded, entries, earnedBadges)
                )
            }
        )
    }
    return requirements.map { it.toReport(partOfHasEnough = false) }
}

private fun TrackerDefinition.toReport(entries: List<TrackerEntry>): ReportTracker {
    // The rows filled in, without the empty rows of a tracker with a fixed number of them.
    val rows = numberedRows(entries).mapNotNull { (number, entry) ->
        entry?.let { ReportTrackerRow(number, columnValues(it)) }
    }
    return ReportTracker(this, rows, totals(entries))
}
