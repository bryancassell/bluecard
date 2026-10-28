package io.github.bryancassell.bluecard.data

import io.github.bryancassell.bluecard.di.WarningLog
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.SYSTEM as okioSystem

/**
 * The device's own file system. Okio's for common code is an extension, imported here under its
 * own name: on Android, FileSystem's own SYSTEM, the same object, would take precedence and leave
 * the import unused, so an IDE would remove it, though common code needs it (checked in Okio
 * 3.18.2).
 */
internal val systemFileSystem: FileSystem get() = FileSystem.okioSystem

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
