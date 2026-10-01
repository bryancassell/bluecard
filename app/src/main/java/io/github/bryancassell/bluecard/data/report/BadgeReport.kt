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
import io.github.bryancassell.bluecard.data.progress.filledRows
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
    return BadgeReport(
        profile = profile,
        badgeName = name,
        requirementsVersion = version.effectiveDate,
        completion = progress.completion(version),
        counselor = progress.badge.counselor,
        requirements = version.requirements.map { it.toReport(recorded, entries) },
        createdDate = createdDate
    )
}

private fun Requirement.toReport(
    recorded: Map<String, RequirementProgress>,
    entries: Map<String, List<TrackerEntry>>
): ReportRequirement = ReportRequirement(
    number = number,
    summary = summary,
    // A count of all the children is no choice.
    requiredCount = requiredCount?.takeIf { it < children.size },
    completion = completion(recorded, entries),
    comment = recorded[number]?.comment,
    tracker = tracker?.toReport(entries[number].orEmpty()),
    children = children.map { it.toReport(recorded, entries) }
)

private fun TrackerDefinition.toReport(entries: List<TrackerEntry>): ReportTracker {
    val numbered = if (rowCount == null) {
        entries.sortedBy { it.id }.mapIndexed { index, entry -> index + 1 to entry }
    } else {
        filledRows(entries, rowCount).toList().sortedBy { (number, _) -> number }
    }
    val rows = numbered.map { (number, entry) ->
        ReportTrackerRow(
            number,
            columns.mapNotNull { column -> entry.values[column.id]?.let { column to it } }
        )
    }
    return ReportTracker(this, rows)
}
