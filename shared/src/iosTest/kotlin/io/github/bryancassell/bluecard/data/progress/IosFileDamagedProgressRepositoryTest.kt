package io.github.bryancassell.bluecard.data.progress

import okio.Path

/** [FileDamagedProgressRepositoryTest] on the iOS simulator. */
class IosFileDamagedProgressRepositoryTest : FileDamagedProgressRepositoryTest() {
    // Okio's POSIX file system gives the system's description of the error, without the path
    // (Okio 3.18.2's errnoToIOException).
    override fun cantCreateDirectoryMessage(directory: Path) = "Permission denied"
}
