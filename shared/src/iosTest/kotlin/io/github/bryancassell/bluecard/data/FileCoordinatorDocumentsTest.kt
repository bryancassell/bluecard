package io.github.bryancassell.bluecard.data

import io.github.bryancassell.bluecard.di.NSLogWarningLog
import kotlin.coroutines.ContinuationInterceptor
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import platform.Foundation.NSFileCoordinatorWritingForDeleting
import platform.Foundation.NSFileCoordinatorWritingForReplacing
import platform.Foundation.NSFileCoordinatorWritingOptions
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.posix.chmod

// The file picker's URLs are files outside the app, which only it can give. These are files in
// the app's temporary folder, which need no access granted, so they test what's done with a
// file, not the access.
class FileCoordinatorDocumentsTest {
    private val fileSystem = systemFileSystem
    private val folder =
        NSTemporaryDirectory().toPath() / "FileCoordinatorDocumentsTest-${Random.nextLong()}"
    private val ioDispatcher = Dispatchers.Default
    private val coordinator = RecordingCoordinator()
    private val documents = FileCoordinatorDocuments(NSLogWarningLog, ioDispatcher, coordinator)

    @AfterTest
    fun deleteFolder() {
        fileSystem.deleteRecursively(folder)
    }

    /** A document holding [text], empty as the file picker makes it by default. */
    private fun document(text: String = ""): Path {
        fileSystem.createDirectories(folder)
        val file = folder / "BlueCard export.json"
        fileSystem.write(file) { writeUtf8(text) }
        return file
    }

    private fun Path.uri() = FileUrl(NSURL.fileURLWithPath(toString()))

    private fun Path.text() = fileSystem.read(this) { readUtf8() }

    @Test
    fun save_writesTheContent_whileCoordinatedForReplacing_andMakesItOnTheIoDispatcher() = runTest {
        val file = document()
        var madeOn: ContinuationInterceptor? = null

        documents.save(file.uri()) {
            madeOn = currentCoroutineContext()[ContinuationInterceptor]
            "Saved".encodeToByteArray()
        }

        assertEquals("Saved", file.text())
        assertSame(ioDispatcher, madeOn)
        assertEquals(listOf(Held(file, NSFileCoordinatorWritingForReplacing)), coordinator.held)
    }

    @Test
    fun save_replacesALongerFile() = runTest {
        val file = document("An older, longer export")

        documents.save(file.uri()) { "Saved".encodeToByteArray() }

        assertEquals("Saved", file.text())
    }

    @Test
    fun save_withNoContent_deletesTheEmptyDocument_whileCoordinatedForDeleting() = runTest {
        val file = document()

        documents.save(file.uri()) { null }

        assertFalse(fileSystem.exists(file))
        assertEquals(listOf(Held(file, NSFileCoordinatorWritingForDeleting)), coordinator.held)
    }

    @Test
    fun save_withNoContent_keepsADocumentThatIsntEmpty() = runTest {
        val file = document("An older export")

        documents.save(file.uri()) { null }

        assertEquals("An older export", file.text())
    }

    @Test
    fun save_whenTheContentCantBeMade_deletesTheEmptyDocument_andThrows() = runTest {
        val file = document()

        assertFailsWith<IOException> {
            documents.save(file.uri()) { throw IOException("Progress couldn't be read") }
        }

        assertFalse(fileSystem.exists(file))
    }

    @Test
    fun save_whenTheContentCantBeMade_keepsADocumentThatIsntEmpty() = runTest {
        val file = document("An older export")

        assertFailsWith<IllegalStateException> {
            documents.save(file.uri()) { error("No profile") }
        }

        assertEquals("An older export", file.text())
    }

    @Test
    fun save_whenTheDocumentCantBeOpened_deletesTheEmptyDocument_andThrowsIOException() = runTest {
        val file = document()
        chmod(file.toString(), READ_ONLY)

        assertFailsWith<IOException> {
            documents.save(file.uri()) { "Saved".encodeToByteArray() }
        }

        assertFalse(fileSystem.exists(file))
    }

    @Test
    fun save_whenTheDocumentCantBeCoordinated_throwsIOException_andKeepsIt() = runTest {
        val file = document()
        coordinator.failing = true

        assertFailsWith<IOException> {
            documents.save(file.uri()) { "Saved".encodeToByteArray() }
        }

        assertEquals("", file.text())
    }

    @Test
    fun open_readsTheDocument_whileCoordinatedForReading() {
        val file = document("An export")

        val text = documents.open(file.uri()).buffer().use { it.readUtf8() }

        assertEquals("An export", text)
        assertEquals(listOf(Held(file, writing = null)), coordinator.held)
    }

    @Test
    fun open_whenTheDocumentIsGone_throwsIOException() {
        val file = folder / "BlueCard export.json"

        assertFailsWith<IOException> { documents.open(file.uri()) }
    }

    @Test
    fun open_whenTheDocumentCantBeCoordinated_throwsIOException() {
        val file = document("An export")
        coordinator.failing = true

        assertFailsWith<IOException> { documents.open(file.uri()) }
    }

    @Test
    fun open_keepsReadingAfterTheDocumentIsReplaced() {
        val file = document("An export")
        val source = documents.open(file.uri()).buffer()

        // As an app that saves the file replaces it.
        val replacement = folder / "replacement"
        fileSystem.write(replacement) { writeUtf8("Another export") }
        fileSystem.atomicMove(replacement, file)

        assertEquals("An export", source.use { it.readUtf8() })
    }

    /** A file [RecordingCoordinator] held, and how. */
    private data class Held(val file: Path, val writing: NSFileCoordinatorWritingOptions?)

    /**
     * In iOS's file coordinator's place, which fails each file in tests. It runs each block
     * right away, or throws as iOS's does if it's [failing].
     */
    private inner class RecordingCoordinator : FileCoordinator {
        var failing = false
        val held = mutableListOf<Held>()

        override fun <T> coordinate(
            url: NSURL,
            writing: NSFileCoordinatorWritingOptions?,
            block: (Path) -> T
        ): T {
            if (failing) throw IOException("Couldn't reach $url")
            val file = checkNotNull(url.path).toPath()
            held += Held(file, writing)
            return block(file)
        }
    }

    private companion object {
        val READ_ONLY = "444".toUShort(radix = 8)
    }
}
