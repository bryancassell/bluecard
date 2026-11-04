package io.github.bryancassell.bluecard.data.progress

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import okio.Path
import platform.posix.S_IWUSR
import platform.posix.chmod
import platform.posix.stat

// Changes only the owner's write permission, as the JVM's File.setWritable does.
@OptIn(ExperimentalForeignApi::class)
actual fun setWritable(path: Path, writable: Boolean): Boolean = memScoped {
    val status = alloc<stat>()
    if (stat(path.toString(), status.ptr) != 0) return false
    val ownerWrite = S_IWUSR.toUShort()
    val mode = status.st_mode
    chmod(path.toString(), if (writable) mode or ownerWrite else mode and ownerWrite.inv()) == 0
}
