package io.github.bryancassell.bluecard.data.report

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.Completion
import io.github.bryancassell.bluecard.data.progress.Counselor
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import io.github.bryancassell.bluecard.data.progress.TrackerEntry
import io.github.bryancassell.bluecard.data.progress.completion
import io.github.bryancassell.bluecard.data.progress.hasEnoughChildren
import io.github.bryancassell.bluecard.data.progress.hasPartDone
import io.github.bryancassell.bluecard.data.progress.numberedRows
import io.github.bryancassell.bluecard.data.progress.requirementsVersionFor
import java.time.LocalDate

/** Everything a badge's report shows: the scout, the badge, and all they recorded for it. */
data class BadgeReport(
    val profile: Profile,
    val badgeName: String,
    /** Effective date of the requirements version the badge is worked on. */
    val requirementsVersion: LocalDate,
    /** When the badge was completed, or null if it isn't complete. */
    val completion: Completion?,
    val counselor: Counselor?,
    /** Every requirement of the version, in catalog order, with its sub-requirements. */
    val requirements: List<ReportRequirement>,
    /** The day the report was created. */
    val createdDate: LocalDate
)

/** A requirement in a report, with what the scout recorded for it. */
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
     * the scout marked the badge completed on a prior date, without recording its requirements.
     * The screens say so too.
     */
    val notRecorded: Boolean,
    val comment: String?,
    /** Its tracker, or null if it has none. */
    val tracker: ReportTracker?,
    val children: List<ReportRequirement>
)

/** A requirement's tracker, with the rows the scout filled in. */
data class ReportTracker(
    val definition: TrackerDefinition,
    /** The rows filled in, in order: by row number, or in a log in the order they were added. */
    val rows: List<ReportTrackerRow>
)

/** A filled-in tracker row. */
data class ReportTrackerRow(
    /** The row it fills, from 1, or its place in a log. */
    val number: Int,
    /** Its values as stored, in column order, without the columns it has none for. */
    val values: List<Pair<TrackerColumn, String>>
)

/**
 * The report on this badge for [profile], from their [progress] on it, created on
 * [createdDate]. Null if the catalog doesn't have the version the badge was started on, which
 * only a catalog edited during development can cause.
 */
fun MeritBadge.report(
    profile: Profile,
    progress: BadgeProgressDetails,
    createdDate: LocalDate
): BadgeReport? {
    val version = requirementsVersionFor(progress) ?: return null
    val recorded = progress.requirements.associateBy { it.requirementNumber }
    val entries = progress.trackerEntries.groupBy { it.requirementNumber }
    val completedOnPriorDate = progress.badge.completedOnPriorDate != null
    return BadgeReport(
        profile = profile,
        badgeName = name,
        requirementsVersion = version.effectiveDate,
        completion = progress.completion(version),
        counselor = progress.badge.counselor,
        requirements = version.requirements.map {
            it.toReport(
                recorded,
                entries,
                partOfHasEnough = false,
                badgeCompletedOnPriorDate = completedOnPriorDate
            )
        },
        createdDate = createdDate
    )
}

/**
 * [partOfHasEnough] is whether a requirement this one is part of, at any depth, has enough
 * complete sub-requirements ([hasEnoughChildren]). [badgeCompletedOnPriorDate] is whether the
 * scout marked the badge completed on a prior date. They're in the same order as in the
 * screens' `toItem`, which works out the same states.
 */
private fun Requirement.toReport(
    recorded: Map<String, RequirementProgress>,
    entries: Map<String, List<TrackerEntry>>,
    partOfHasEnough: Boolean,
    badgeCompletedOnPriorDate: Boolean
): ReportRequirement {
    val completion = completion(recorded, entries)
    val stillNeeded = !partOfHasEnough && completion == null
    return ReportRequirement(
        number = number,
        summary = summary,
        requiredCount = choiceCount,
        completion = completion,
        notNeeded = partOfHasEnough && completion == null,
        notRecorded = badgeCompletedOnPriorDate && stillNeeded && !hasPartDone(recorded, entries),
        comment = recorded[number]?.comment,
        tracker = tracker?.toReport(entries[number].orEmpty()),
        children = children.map {
            it.toReport(
                recorded,
                entries,
                partOfHasEnough = partOfHasEnough || hasEnoughChildren(recorded, entries),
                badgeCompletedOnPriorDate = badgeCompletedOnPriorDate
            )
        }
    )
}

private fun TrackerDefinition.toReport(entries: List<TrackerEntry>): ReportTracker {
    // The rows filled in, without the empty rows of a tracker with a fixed number of them.
    val rows = numberedRows(entries).mapNotNull { (number, entry) ->
        entry?.let { ReportTrackerRow(number, values(it)) }
    }
    return ReportTracker(this, rows)
}

private fun TrackerDefinition.values(entry: TrackerEntry): List<Pair<TrackerColumn, String>> =
    columns.mapNotNull { column -> entry.values[column.id]?.let { column to it } }
