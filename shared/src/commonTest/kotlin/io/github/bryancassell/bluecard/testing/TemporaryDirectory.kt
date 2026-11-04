package io.github.bryancassell.bluecard.testing

import io.github.bryancassell.bluecard.data.systemFileSystem
import kotlin.random.Random
import kotlin.random.nextULong
import okio.FileSystem
import okio.Path

/**
 * A new, empty directory of a test's own, as JUnit's TemporaryFolder makes, for common tests,
 * which can't use JUnit. The test calls [delete] when it's done.
 */
class TemporaryDirectory {
    val path: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "bluecard-test-${Random.nextULong()}"

    init {
        systemFileSystem.createDirectory(path, mustCreate = true)
    }

    /** Deletes the directory and everything in it. */
    fun delete() = systemFileSystem.deleteRecursively(path)
}
