package io.github.bryancassell.bluecard.data.progress

import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.posix.chmod

/** [FileDamagedProgressRepositoryTest] with POSIX and Foundation, on the iOS simulator. */
@OptIn(ExperimentalForeignApi::class)
class IosFileDamagedProgressRepositoryTest : FileDamagedProgressRepositoryTest() {
    override fun setWritable(path: Path, writable: Boolean) {
        val mode = if (writable) 0b111_000_000u else 0b101_000_000u
        check(chmod(path.toString(), mode.toUShort()) == 0) { "Couldn't change $path" }
    }

    override fun setLastModified(path: Path, millis: Long) {
        val date = NSDate.dateWithTimeIntervalSince1970(millis / 1000.0)
        val dated = NSFileManager.defaultManager
            .setAttributes(mapOf(NSFileModificationDate to date), path.toString(), null)
        check(dated) { "Couldn't date $path" }
    }

    // Okio's POSIX file system gives the system's description of the error, without the path.
    override fun cantCreateDirectoryMessage(directory: Path) = "Permission denied"
}
