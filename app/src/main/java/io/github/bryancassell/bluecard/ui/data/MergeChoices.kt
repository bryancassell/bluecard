package io.github.bryancassell.bluecard.ui.data

import io.github.bryancassell.bluecard.data.backup.Backup
import io.github.bryancassell.bluecard.data.backup.mergeConflicts
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Rank
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.BadgeStatus
import io.github.bryancassell.bluecard.data.progress.RankStatus
import io.github.bryancassell.bluecard.data.progress.earnedBadges
import io.github.bryancassell.bluecard.data.progress.fractionDoneWhileInProgress
import io.github.bryancassell.bluecard.data.progress.standings
import io.github.bryancassell.bluecard.data.progress.status
import java.time.LocalDate

/**
 * What a merge of the file the scout chose ([backup]) asks them to choose, where the phone and
 * the file differ: the name and unit number, and each badge and rank started on both. Each
 * starts on the phone's.
 */
data class MergeChoices(
    val backup: Backup,
    /** The name and unit number's choice, or null if the file's are the phone's. */
    val profile: ProfileChoice?,
    /** Each badge, then each rank, in the catalog's order. */
    val advancements: List<AdvancementChoice>
) {
    /** Whether there's nothing to choose, so the merge needs no choices. */
    val isEmpty: Boolean get() = profile == null && advancements.isEmpty()

    /** The IDs of the badges and ranks the scout chose the file's progress for. */
    val fromFile: Set<String> get() = advancements.filter { it.fromFile }.map { it.id }.toSet()
}

/** The phone's and the file's name and unit number, and which the scout chose. */
data class ProfileChoice(val phone: Profile, val file: Profile, val fromFile: Boolean = false)

/** A badge or rank started on both sides, how far each got with it, and which the scout chose. */
data class AdvancementChoice(
    val id: String,
    val name: String,
    /** Whether it's a rank, which is earned where a badge is completed. */
    val isRank: Boolean,
    val phone: ProgressSummary,
    val file: ProgressSummary,
    val fromFile: Boolean = false
)

/** How far one side got with a badge or rank, as the Badges and Ranks lists show it. */
data class ProgressSummary(
    /** Whether the badge is completed, or the rank earned. */
    val done: Boolean,
    /** How much of it is done, from 0 to 1, while it isn't; null if that can't be measured. */
    val fractionDone: Float? = null,
    /** Its requirements version, when the phone's and the file's differ; null otherwise. */
    val requirementsVersion: LocalDate? = null
)

/**
 * The choices merging [file] into the [phone]'s data asks for, with the [badges] and [ranks] in
 * the catalog. Each side's badges and ranks are summed up from that side's progress alone, as
 * its own lists would show them.
 */
fun mergeChoices(
    badges: List<MeritBadge>,
    ranks: List<Rank>,
    phone: Backup,
    file: Backup
): MergeChoices {
    val conflicts = mergeConflicts(phone.progress, file.progress)
    val onPhone = Side(badges, ranks, phone.progress)
    val inFile = Side(badges, ranks, file.progress)
    val advancements = (badges + ranks).filter { it.id in conflicts }.map { advancement ->
        val phoneProgress = onPhone.progress.getValue(advancement.id)
        val fileProgress = inFile.progress.getValue(advancement.id)
        val phoneVersion = phoneProgress.badge.requirementsVersion
        val fileVersion = fileProgress.badge.requirementsVersion
        val versionsDiffer = phoneVersion != fileVersion
        AdvancementChoice(
            id = advancement.id,
            name = advancement.name,
            isRank = advancement is Rank,
            phone = onPhone.summary(advancement.id, phoneVersion.takeIf { versionsDiffer }),
            file = inFile.summary(advancement.id, fileVersion.takeIf { versionsDiffer })
        )
    }
    val profileDiffers = phone.profile != file.profile
    val profile = if (profileDiffers) ProfileChoice(phone.profile, file.profile) else null
    return MergeChoices(file, profile, advancements)
}

/** One side's progress, with its badges' and ranks' standing worked out once. */
private class Side(
    private val badges: List<MeritBadge>,
    ranks: List<Rank>,
    progress: List<BadgeProgressDetails>
) {
    val progress = progress.associateBy { it.badge.badgeId }
    private val standings = ranks.standings(this.progress, badges.earnedBadges(this.progress))
        .associateBy { it.rank.id }

    fun summary(id: String, requirementsVersion: LocalDate?): ProgressSummary {
        standings[id]?.let { standing ->
            val earned = standing.status == RankStatus.Earned
            return ProgressSummary(earned, standing.fractionDone, requirementsVersion)
        }
        val badge = badges.first { it.id == id }
        val details = progress.getValue(id)
        return ProgressSummary(
            badge.status(details) == BadgeStatus.Completed,
            badge.fractionDoneWhileInProgress(details),
            requirementsVersion
        )
    }
}
