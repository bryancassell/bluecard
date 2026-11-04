package io.github.bryancassell.bluecard.data.progress

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import okio.Path
import platform.posix.S_IWUSR
import platform.posix.chmod
import platform.posix.stat

/** [FileDamagedProgressRepositoryTest] with POSIX, on the iOS simulator. */
class IosFileDamagedProgressRepositoryTest : FileDamagedProgressRepositoryTest() {
    // Changes only the owner's write permission, as the JVM's File.setWritable does.
    @OptIn(ExperimentalForeignApi::class)
    override fun setWritable(path: Path, writable: Boolean): Boolean = memScoped {
        val status = alloc<stat>()
        if (stat(path.toString(), status.ptr) != 0) return false
        val ownerWrite = S_IWUSR.toUShort()
        val mode = status.st_mode
        chmod(path.toString(), if (writable) mode or ownerWrite else mode and ownerWrite.inv()) == 0
    }

    // Okio's POSIX file system gives the system's description of the error, without the path
    // (Okio 3.18.2's errnoToIOException).
    override fun cantCreateDirectoryMessage(directory: Path) = "Permission denied"
}
