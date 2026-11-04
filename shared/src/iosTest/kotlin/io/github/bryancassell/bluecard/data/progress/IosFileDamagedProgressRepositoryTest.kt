package io.github.bryancassell.bluecard.data.progress

import okio.Path
import platform.posix.chmod

/** [FileDamagedProgressRepositoryTest] with POSIX, on the iOS simulator. */
class IosFileDamagedProgressRepositoryTest : FileDamagedProgressRepositoryTest() {
    override fun setWritable(path: Path, writable: Boolean): Boolean {
        val mode = if (writable) 0b111_000_000u else 0b101_000_000u
        return chmod(path.toString(), mode.toUShort()) == 0
    }

    // Okio's POSIX file system gives the system's description of the error, without the path
    // (Okio 3.18.2's errnoToIOException).
    override fun cantCreateDirectoryMessage(directory: Path) = "Permission denied"
}
