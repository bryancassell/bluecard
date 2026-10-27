package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.Composable
import io.github.bryancassell.bluecard.data.DocumentUri

/**
 * The system file picker, which a screen opens through its [OtherAppStarter]. Made with
 * [rememberCreateDocument] or [rememberOpenDocument].
 */
fun interface DocumentPicker<I> {
    /**
     * Opens the file picker with [input], such as the name of the file to create. Returns false
     * if no app on the phone can.
     */
    fun launch(input: I): Boolean
}

/**
 * The file picker, to create a document of [mimeType] where the scout chooses, suggesting the
 * name it's launched with. [onCreated] gets the new document, unless the scout leaves the file
 * picker without saving.
 */
@Composable
expect fun rememberCreateDocument(
    mimeType: String,
    onCreated: (DocumentUri) -> Unit
): DocumentPicker<String>

/**
 * The file picker, to open a document of one of the MIME types it's launched with. [onOpened]
 * gets the document, unless the scout leaves the file picker without choosing one.
 */
@Composable
expect fun rememberOpenDocument(onOpened: (DocumentUri) -> Unit): DocumentPicker<Array<String>>
