package io.github.bryancassell.bluecard.data.backup

import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails

/** Everything an export holds. */
data class Backup(val profile: Profile, val progress: List<BadgeProgressDetails>)

/** What [BackupRepository.readBackup] found in a file. */
sealed interface BackupReadResult {
    /** An export this app can import. */
    data class Valid(val backup: Backup) : BackupReadResult

    /** Not a BlueCard export, or one that was changed or damaged. */
    data object Invalid : BackupReadResult

    /**
     * An export from a newer version of the app: in a format this one can't read, or with a
     * badge or requirements version that this one's catalog doesn't have.
     */
    data object NewerFormat : BackupReadResult
}
