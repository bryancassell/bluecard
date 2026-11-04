package io.github.bryancassell.bluecard.data.progress

import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.dateWithTimeIntervalSince1970

@OptIn(ExperimentalForeignApi::class)
actual fun setLastModified(file: Path, millis: Long) {
    val date = NSDate.dateWithTimeIntervalSince1970(millis / 1000.0)
    val dated = NSFileManager.defaultManager
        .setAttributes(mapOf(NSFileModificationDate to date), file.toString(), null)
    check(dated) { "Couldn't date $file" }
}
