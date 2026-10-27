package io.github.bryancassell.bluecard.data

import okio.Source

/**
 * A document the scout chose with the platform's file picker, or a report to share, as the
 * platform names it: Android's `Uri`. Common code only passes it on, to [Documents] or to the
 * screen that shares it. It's the platform's own type rather than the URI's text, so Android has
 * nothing to convert, and an iOS file picker's URL can keep the access to the file that it
 * carries, which a URL rebuilt from text may not.
 */
expect abstract class DocumentUri {
    /** The URI as text. Abstract, as Android's `Uri` declares it. */
    abstract override fun toString(): String
}

/**
 * Reads and writes documents the scout chose with the platform's file picker. An interface, so
 * each platform reaches them its own way: Android through the content resolver.
 */
interface Documents {
    /**
     * Writes [content] to [destination], replacing what it held. The content is made before the
     * destination is opened, which empties it, so content that can't be made leaves a file the
     * scout chose to replace as it was. So does no content (null), such as a report on a badge
     * that was just cleared, which writes nothing.
     *
     * Throws what making the content throws, or an `IOException` if the document can't be
     * opened or written. Either way, it doesn't leave behind the empty document the file picker
     * made, or one that opening emptied.
     */
    suspend fun save(destination: DocumentUri, content: suspend () -> ByteArray?)

    /** Opens [source] to read. Throws an `IOException` if it can't be opened. */
    fun open(source: DocumentUri): Source
}
