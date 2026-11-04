package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.testing.FakeWarningLog
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okio.Path

/** [FileDamagedProgressRepositoryTest] with java.io.File, on the JVM. */
class AndroidFileDamagedProgressRepositoryTest : FileDamagedProgressRepositoryTest() {
    override fun setWritable(path: Path, writable: Boolean) =
        File(path.toString()).setWritable(writable)

    // Okio's JVM file system names the directory (Okio 3.18.2).
    override fun cantCreateDirectoryMessage(directory: Path) =
        "failed to create directory: $directory"

    // Only the JVM has interrupts, so only here can the test see that nothing was deleted.
    @Test
    fun clearLeftoverDamageRecord_withNoRecord_deletesNothing() {
        writeDatabase("" to "progress")
        // Okio's delete would take the interrupt for itself and fail (Okio 3.18.2).
        Thread.currentThread().interrupt()
        val stillInterrupted = try {
            repository.clearLeftoverDamageRecord(database)
            Thread.interrupted()
        } finally {
            // So the interrupt can't reach the next test.
            Thread.interrupted()
        }

        assertTrue(stillInterrupted)
        assertEquals(emptyList<FakeWarningLog.Warning>(), warningLog.warnings)
    }
}
