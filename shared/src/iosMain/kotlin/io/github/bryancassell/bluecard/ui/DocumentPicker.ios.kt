package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.Composable
import io.github.bryancassell.bluecard.data.DocumentUri

// Until #348, iOS has no file picker, so each reports that none could open.

@Composable
actual fun rememberCreateDocument(
    mimeType: String,
    onCreated: (DocumentUri) -> Unit
): DocumentPicker<String> = DocumentPicker { false }

@Composable
actual fun rememberOpenDocument(onOpened: (DocumentUri) -> Unit): DocumentPicker<Array<String>> =
    DocumentPicker { false }
