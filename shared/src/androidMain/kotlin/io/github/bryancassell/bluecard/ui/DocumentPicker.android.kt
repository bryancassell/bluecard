package io.github.bryancassell.bluecard.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.bryancassell.bluecard.data.DocumentUri

@Composable
actual fun rememberCreateDocument(
    mimeType: String,
    onCreated: (DocumentUri) -> Unit
): DocumentPicker<String> {
    val launcher = rememberLauncherForActivityResult(CreateDocument(mimeType)) { destination ->
        // Null when the scout leaves the file picker without saving.
        destination?.let(onCreated)
    }
    return remember(launcher) { DocumentPicker { startsApp { launcher.launch(it) } } }
}

@Composable
actual fun rememberOpenDocument(onOpened: (DocumentUri) -> Unit): DocumentPicker<Array<String>> {
    val launcher = rememberLauncherForActivityResult(OpenOpenableDocument()) { source ->
        // Null when the scout leaves the file picker without choosing a file.
        source?.let(onOpened)
    }
    return remember(launcher) { DocumentPicker { startsApp { launcher.launch(it) } } }
}

/**
 * Opens a document that can be read as a file, as the [ACTION_OPEN_DOCUMENT] docs say to ask
 * for: the file picker leaves out virtual documents, such as a Google Doc, which can't be.
 *
 * [ACTION_OPEN_DOCUMENT]: https://developer.android.com/reference/android/content/Intent#ACTION_OPEN_DOCUMENT
 */
private class OpenOpenableDocument : OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).addCategory(Intent.CATEGORY_OPENABLE)
}
