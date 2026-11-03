package io.github.bryancassell.bluecard.data

import platform.Foundation.NSURL

/** On iOS, a document is the file URL the file picker gives ([FileUrl]). */
actual abstract class DocumentUri {
    /** The document's file URL, with the access to the file that the file picker gave it. */
    abstract val url: NSURL

    actual abstract override fun toString(): String
}

/** A file URL as a [DocumentUri]. Equal to another for the same URL, as Android's `Uri` is. */
data class FileUrl(override val url: NSURL) : DocumentUri() {
    override fun toString(): String = url.absoluteString.orEmpty()
}
