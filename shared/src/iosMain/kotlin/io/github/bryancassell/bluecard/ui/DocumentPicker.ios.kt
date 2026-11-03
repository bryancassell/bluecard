package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import io.github.bryancassell.bluecard.data.DocumentUri
import io.github.bryancassell.bluecard.data.FileUrl
import io.github.bryancassell.bluecard.data.deleteOrWarn
import io.github.bryancassell.bluecard.data.systemFileSystem
import io.github.bryancassell.bluecard.di.NSLogWarningLog
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject

// iOS's file picker is always there, so launching it never reports that no app can.

private const val TAG = "DocumentPicker"

@Composable
actual fun rememberCreateDocument(
    mimeType: String,
    onCreated: (DocumentUri) -> Unit
): DocumentPicker<String> {
    val viewController = LocalUIViewController.current
    val currentOnCreated by rememberUpdatedState(onCreated)
    return remember(viewController) {
        FilePicker(viewController) { fileName -> createDocument(fileName) { currentOnCreated(it) } }
    }
}

@Composable
actual fun rememberOpenDocument(onOpened: (DocumentUri) -> Unit): DocumentPicker<Array<String>> {
    val viewController = LocalUIViewController.current
    val currentOnOpened by rememberUpdatedState(onOpened)
    return remember(viewController) {
        FilePicker(viewController) { mimeTypes -> openDocument(mimeTypes) { currentOnOpened(it) } }
    }
}

/**
 * The file picker, to save a new document named [fileName] where the scout chooses. iOS's file
 * picker can't make an empty document there, as Android's does, so this makes one in the app's
 * temporary folder ([folder]) and has the picker move it (`asCopy` false, which the
 * picker's docs call exporting). [onCreated] gets the moved document, whose URL carries access
 * to write it. Its type comes from its name's extension.
 *
 * Returns null, and writes why to the log, if the document can't be made, which leaves the tap
 * doing nothing.
 */
internal fun createDocument(
    fileName: String,
    folder: Path = newDocumentsFolder(),
    onCreated: (DocumentUri) -> Unit
): FilePicker.Launch? {
    val file = folder / fileName
    try {
        systemFileSystem.createDirectories(folder)
        systemFileSystem.write(file) {}
    } catch (e: IOException) {
        NSLogWarningLog.warn(TAG, "Couldn't make $file to save", e)
        return null
    }
    return FilePicker.Launch(
        picker = UIDocumentPickerViewController(
            forExportingURLs = listOf(NSURL.fileURLWithPath(file.toString())),
            asCopy = false
        ),
        onPicked = { onCreated(FileUrl(it)) },
        // Moved if the scout saved it, and still here if they didn't.
        onClosed = { systemFileSystem.deleteOrWarn(file, NSLogWarningLog, TAG) }
    )
}

/**
 * The file picker, to open a document of one of [mimeTypes]. [onOpened] gets it where it is,
 * rather than a copy, as the file picker's docs advise, and its URL carries access to read it.
 */
internal fun openDocument(mimeTypes: Array<String>, onOpened: (DocumentUri) -> Unit) =
    FilePicker.Launch(
        picker = UIDocumentPickerViewController(
            forOpeningContentTypes = mimeTypes.map(::contentType),
            asCopy = false
        ),
        onPicked = { onOpened(FileUrl(it)) },
        onClosed = {}
    )

/**
 * The type the file picker offers for [mimeType]. Android's wildcard for any type is any file
 * that can be read as bytes, which leaves out folders, as Android's file picker does with
 * `CATEGORY_OPENABLE`. So is a type iOS can't look up, rather than one the scout can't pick: a
 * file is checked once it's read, as Import checks it.
 */
internal fun contentType(mimeType: String): UTType =
    if (mimeType == "*/*") UTTypeData else UTType.typeWithMIMEType(mimeType) ?: UTTypeData

/** Where a new document waits for the file picker to move it where the scout chooses. */
internal fun newDocumentsFolder(): Path = NSTemporaryDirectory().toPath() / "new-documents"

/**
 * Shows the file picker that [prepare] makes from each launch's input over [viewController], or
 * nothing if [prepare] returns null.
 */
internal class FilePicker<I>(
    private val viewController: UIViewController,
    private val prepare: (I) -> Launch?
) : DocumentPicker<I> {
    /** A file picker to show, what to do when the scout picks a file, and when it closes. */
    class Launch(
        val picker: UIDocumentPickerViewController,
        val onPicked: (NSURL) -> Unit,
        val onClosed: () -> Unit
    )

    // The file picker holds its delegate weakly, so this holds each until its own picker
    // closes, even one UIKit didn't yet report as presented when the next launch came.
    private val shown = mutableSetOf<Delegate>()

    override fun launch(input: I): Boolean {
        // Another picker can't show over one that does. UIKit lists a picker as presented only
        // once it has appeared, so a launch while one appears gets past this, and UIKit refuses
        // to show it.
        if (viewController.presentedViewController != null) return true
        val launch = prepare(input) ?: return true
        val delegate = Delegate(launch) { shown -= it }
        shown += delegate
        launch.picker.delegate = delegate
        viewController.presentViewController(launch.picker, animated = true, completion = null)
        return true
    }

    private class Delegate(private val launch: Launch, private val onDone: (Delegate) -> Unit) :
        NSObject(),
        UIDocumentPickerDelegateProtocol {
        override fun documentPicker(
            controller: UIDocumentPickerViewController,
            didPickDocumentsAtURLs: List<*>
        ) {
            (didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.let(launch.onPicked)
            close()
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            close()
        }

        private fun close() {
            launch.onClosed()
            onDone(this)
        }
    }
}
