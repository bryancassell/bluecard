package io.github.bryancassell.bluecard.data.backup

import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails

/**
 * The IDs of the badges and ranks that a merge of [file]'s progress into the [phone]'s asks the
 * scout about: those started on both, with different progress. One with the same progress on
 * both, such as one unchanged since the file was exported, needs no choice.
 */
fun mergeConflicts(
    phone: List<BadgeProgressDetails>,
    file: List<BadgeProgressDetails>
): Set<String> {
    val onPhone = phone.associateBy { it.badge.badgeId }
    return file.filter { onPhone[it.badge.badgeId]?.recordsSameAs(it) == false }
        .map { it.badge.badgeId }
        .toSet()
}

/**
 * Whether this progress and [other] record the same, as the scout sees it: in any order, except
 * for each log's entries, and with any tracker entry IDs, which an import replaces.
 */
private fun BadgeProgressDetails.recordsSameAs(other: BadgeProgressDetails) =
    badge == other.badge &&
        requirements.toSet() == other.requirements.toSet() &&
        trackerRows() == other.trackerRows()

/**
 * The tracker entries without their IDs, by requirement and then row, with each log's in the
 * order they were added.
 */
private fun BadgeProgressDetails.trackerRows() = trackerEntries.sortedBy { it.id }
    .sortedWith(compareBy({ it.requirementNumber }, { it.rowNumber }))
    .map { it.copy(id = 0) }
