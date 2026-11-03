package io.github.bryancassell.bluecard.ui

import io.github.bryancassell.bluecard.data.DocumentUri
import io.github.bryancassell.bluecard.data.FileUrl
import io.github.bryancassell.bluecard.data.systemFileSystem
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeData

// The file picker can't be shown here, with no app window, so these call its delegate as it
// would.
class DocumentPickerTest {
    private val fileSystem = systemFileSystem

    // Not the new documents folder: Gradle runs every checkout's iOS tests on the one simulator.
    private val folder = NSTemporaryDirectory().toPath() / "DocumentPickerTest-${Random.nextLong()}"
    private val fileName = "BlueCard export 2026-11-03.json"
    private val pickedUrl = NSURL.fileURLWithPath("/private/var/mobile/BlueCard export.json")
    private val events = mutableListOf<String>()

    @AfterTest
    fun deleteFolder() {
        fileSystem.deleteRecursively(folder)
    }

    /** A file picker [FilePicker] shows for each launch, which records what's done. */
    private val shown = mutableListOf<FilePicker.Launch>()

    private val filePicker = FilePicker<String>(UIViewController()) {
        FilePicker.Launch(
            picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData)),
            onPicked = { url -> events += "picked ${url.lastPathComponent}" },
            onClosed = { events += "closed" }
        ).also { shown += it }
    }

    private val FilePicker.Launch.delegate
        get() = assertNotNull(picker.delegate as UIDocumentPickerDelegateProtocol?)

    @Test
    fun createDocument_makesAnEmptyDocumentWithTheName_forThePickerToMove() {
        assertNotNull(createDocument(fileName, folder) {})

        assertEquals(0L, fileSystem.metadata(folder / fileName).size)
    }

    @Test
    fun createDocument_givesTheDocumentThePickerMoved() {
        var created: DocumentUri? = null
        val launch = assertNotNull(createDocument(fileName, folder) { created = it })

        launch.onPicked(pickedUrl)

        assertEquals(FileUrl(pickedUrl), created)
    }

    @Test
    fun createDocument_whenThePickerCloses_deletesTheDocumentLeftBehind() {
        val launch = assertNotNull(createDocument(fileName, folder) {})

        launch.onClosed()

        assertFalse(fileSystem.exists(folder / fileName))
    }

    @Test
    fun createDocument_whenTheDocumentCantBeMade_returnsNull() {
        // A file where the folder goes.
        fileSystem.write(folder) {}

        assertNull(createDocument(fileName, folder) {})
    }

    @Test
    fun openDocument_givesTheDocumentPicked() {
        var opened: DocumentUri? = null
        val launch = openDocument(arrayOf("*/*")) { opened = it }

        launch.onPicked(pickedUrl)

        assertEquals(FileUrl(pickedUrl), opened)
    }

    @Test
    fun contentType_ofAnyType_isAnyFileOfData() {
        assertEquals(UTTypeData, contentType("*/*"))
    }

    @Test
    fun launch_whenTheScoutPicksAFile_givesIt_thenCloses() {
        assertTrue(filePicker.launch("*/*"))
        val launch = shown.single()

        launch.delegate.documentPicker(launch.picker, didPickDocumentsAtURLs = listOf(pickedUrl))

        assertEquals(listOf("picked BlueCard export.json", "closed"), events)
    }

    @Test
    fun launch_whenTheScoutCancels_onlyCloses() {
        filePicker.launch("*/*")
        val launch = shown.single()

        launch.delegate.documentPickerWasCancelled(launch.picker)

        assertEquals(listOf("closed"), events)
    }

    @Test
    fun launch_withNothingToShow_reportsNoMissingApp() {
        val picker = FilePicker<String>(UIViewController()) { null }

        assertTrue(picker.launch(fileName))
    }
}
