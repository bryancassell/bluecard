package io.github.bryancassell.bluecard.data

import io.github.bryancassell.bluecard.di.WarningLog
import okio.FileSystem
import okio.IOException
import okio.Path

/**
 * Deletes [file] if it's there. Callers clean up after other work, whose own outcome is what
 * matters, so a failure here is only written to [warningLog] under [tag] rather than thrown,
 * where it would hide that outcome.
 */
fun FileSystem.deleteOrWarn(file: Path, warningLog: WarningLog, tag: String) {
    try {
        delete(file)
    } catch (e: IOException) {
        warningLog.warn(tag, "Couldn't delete $file", e)
    }
}
