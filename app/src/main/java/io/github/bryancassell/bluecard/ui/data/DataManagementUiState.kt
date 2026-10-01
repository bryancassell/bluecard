package io.github.bryancassell.bluecard.ui.data

import io.github.bryancassell.bluecard.data.backup.Backup
import java.time.LocalDate

/** What the Data management screen shows: export and import. */
data class DataManagementUiState(
    /** Today, which the name suggested for an export has in it. */
    val today: LocalDate,
    /** Whether an export, a file being read or an import is under way. The buttons wait. */
    val working: Boolean = false,
    /**
     * A file the scout chose that can be imported, until they confirm or cancel replacing
     * their data with it. It can be too large for saved state, so if the system stops the app
     * in the background, the scout picks the file again.
     */
    val backupToImport: Backup? = null,
    /** A message for the scout, until the screen has shown it. */
    val message: DataManagementMessage? = null
)

/**
 * A message for the scout about an export or import. Each is its own object, as a SaveFailure
 * is, so one that repeats the last is shown again.
 */
class DataManagementMessage(val kind: Kind) {
    enum class Kind {
        ExportFailed,

        /** The file the scout chose to import couldn't be read. */
        ReadFailed,

        /** The file the scout chose to import isn't an export. */
        Invalid,

        /** The file the scout chose to import is from a newer version of the app. */
        NewerFormat,

        ImportFailed,
        Imported
    }
}
