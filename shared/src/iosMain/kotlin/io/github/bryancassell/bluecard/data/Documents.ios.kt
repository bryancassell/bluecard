package io.github.bryancassell.bluecard.data

import io.github.bryancassell.bluecard.di.WarningLog
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.ForwardingSource
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import okio.buffer
import okio.use
import platform.Foundation.NSError
import platform.Foundation.NSFileCoordinator
import platform.Foundation.NSFileCoordinatorWritingForDeleting
import platform.Foundation.NSFileCoordinatorWritingForReplacing
import platform.Foundation.NSFileCoordinatorWritingOptions
import platform.Foundation.NSURL

private const val TAG = "Documents"

/**
 * [Documents] on iOS: files the scout chose with the file picker, often outside the app. They're
 * reached as the file picker's docs ask
 * (https://developer.apple.com/documentation/uikit/uidocumentpickerviewcontroller): with the
 * security-scoped access each URL carries, and through [coordinator], so a cloud drive's app
 * downloads a file before it's read, and learns of a write. Saving switches to [ioDispatcher],
 * since coordinating waits on that app.
 */
class FileCoordinatorDocuments internal constructor(
    private val warningLog: WarningLog,
    private val ioDispatcher: CoroutineDispatcher,
    private val coordinator: FileCoordinator
) : Documents {
    override suspend fun save(destination: DocumentUri, content: suspend () -> ByteArray?) =
        withContext(ioDispatcher) {
            val url = destination.url
            url.withAccess {
                // The file picker made an empty document for the content, which isn't left
                // behind when there's no content, or when making it or holding the document
                // fails. Once writing has started, write deals with it.
                var writing = false
                try {
                    val bytes = content()
                    if (bytes == null) {
                        deleteIfEmpty(url)
                    } else {
                        coordinator.write(url, NSFileCoordinatorWritingForReplacing) { file ->
                            writing = true
                            write(file, bytes)
                        }
                    }
                } catch (e: Exception) {
                    if (!writing) deleteIfEmpty(url)
                    throw e
                }
            }
        }

    /**
     * Opens [source] while it's coordinated, so a cloud drive's app downloads it first, and
     * returns it to be read after: the file stays open, and an app that replaces it, as saving
     * a file usually does, leaves what this reads as it was. The access to it lasts until it's
     * closed.
     */
    override fun open(source: DocumentUri): Source {
        val url = source.url
        val accessing = url.startAccessingSecurityScopedResource()
        try {
            val file = coordinator.read(url) { systemFileSystem.source(it) }
            return object : ForwardingSource(file) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        if (accessing) url.stopAccessingSecurityScopedResource()
                    }
                }
            }
        } catch (e: Throwable) {
            if (accessing) url.stopAccessingSecurityScopedResource()
            throw e
        }
    }

    /**
     * Writes [bytes] to [file], replacing what it held. If it can't be opened, it's deleted if
     * it's empty, as the file picker made it. If writing fails, it's deleted: opening it emptied
     * it, so it's empty or partial, and the scout could send it without noticing that it failed.
     */
    private fun write(file: Path, bytes: ByteArray) {
        val sink = try {
            systemFileSystem.sink(file)
        } catch (e: Exception) {
            deleteFileIfEmpty(file)
            throw e
        }
        try {
            sink.buffer().use { it.write(bytes) }
        } catch (e: Exception) {
            systemFileSystem.deleteOrWarn(file, warningLog, TAG)
            throw e
        }
    }

    /**
     * Deletes [document] if it's empty, as the file picker makes it, and keeps it otherwise. If
     * it can't be deleted, the save's own failure is what the scout is told about, so this one is
     * only written to [warningLog].
     */
    private fun deleteIfEmpty(document: NSURL) {
        try {
            coordinator.write(document, NSFileCoordinatorWritingForDeleting, ::deleteFileIfEmpty)
        } catch (e: IOException) {
            warningLog.warn(TAG, "Couldn't delete the document that failed", e)
        }
    }

    // Within a coordinated write: another coordinator would wait for this one to finish.
    private fun deleteFileIfEmpty(file: Path) {
        val size = try {
            systemFileSystem.metadataOrNull(file)?.size
        } catch (e: IOException) {
            warningLog.warn(TAG, "Couldn't find the size of the document that failed", e)
            null
        }
        if (size == 0L) systemFileSystem.deleteOrWarn(file, warningLog, TAG)
    }
}

/** Runs [block] with the security-scoped access to this URL that the file picker gave. */
private inline fun <T> NSURL.withAccess(block: () -> T): T {
    // False for a URL that needs no access granted, such as one in the app's own folders.
    val accessing = startAccessingSecurityScopedResource()
    try {
        return block()
    } finally {
        if (accessing) stopAccessingSecurityScopedResource()
    }
}

/**
 * Holds a file while it's read or written, so other apps that use it, such as a cloud drive's,
 * wait for it, and learn what changed: iOS's file coordinator ([SystemFileCoordinator]). An
 * interface, since iOS's doesn't work in tests: Gradle runs them on a simulator that isn't booted,
 * where it fails each file.
 *
 * Each runs [block] on the file at the URL while it's held, and returns what [block] returns. It
 * throws an [IOException] if the file can't be held, such as one a cloud drive's app can't
 * download.
 */
internal interface FileCoordinator {
    /** Holds the file at [url] for reading. */
    fun <T> read(url: NSURL, block: (Path) -> T): T

    /** Holds the file at [url] for writing, with [options], such as for replacing it. */
    fun <T> write(url: NSURL, options: NSFileCoordinatorWritingOptions, block: (Path) -> T): T
}

/** iOS's file coordinator, `NSFileCoordinator`. */
@OptIn(ExperimentalForeignApi::class)
internal object SystemFileCoordinator : FileCoordinator {
    override fun <T> read(url: NSURL, block: (Path) -> T): T =
        runCoordinated(url, block) { accessor ->
            errorOf { error ->
                NSFileCoordinator(filePresenter = null)
                    .coordinateReadingItemAtURL(url, options = 0u, error, byAccessor = accessor)
            }
        }

    override fun <T> write(
        url: NSURL,
        options: NSFileCoordinatorWritingOptions,
        block: (Path) -> T
    ): T = runCoordinated(url, block) { accessor ->
        errorOf { error ->
            NSFileCoordinator(filePresenter = null)
                .coordinateWritingItemAtURL(url, options, error = error, byAccessor = accessor)
        }
    }
}

/**
 * Runs [block] on the file that [coordinate] gives its accessor, and returns what [block]
 * returns. [coordinate] calls a file coordinator with the accessor, and returns the coordinator's
 * error, if it has one.
 *
 * An exception can't pass through the coordinator's Objective-C code, which would end the app, so
 * [block]'s is caught and thrown once the coordinator returns. If the coordinator doesn't run the
 * accessor, or gives it no file, this throws an [IOException].
 */
internal fun <T> runCoordinated(
    url: NSURL,
    block: (Path) -> T,
    coordinate: (accessor: (NSURL?) -> Unit) -> NSError?
): T {
    var result: Result<T>? = null
    val error = coordinate { coordinatedUrl ->
        result = runCatching {
            val path = coordinatedUrl?.path ?: throw IOException("No file to reach $url")
            block(path.toPath())
        }
    }
    val done = result ?: throw IOException("Couldn't reach $url: $error")
    return done.getOrThrow()
}

/** Calls [call] with a place for an error, and returns the error it puts there, if any. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private inline fun errorOf(call: (CPointer<ObjCObjectVar<NSError?>>) -> Unit): NSError? =
    memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        call(error.ptr)
        error.value
    }
