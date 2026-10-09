package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import io.github.bryancassell.bluecard.R

/**
 * The label of something screen readers read as one, such as a list row: [parts], the text it
 * shows, in reading order, leaving out any that are null. It's set with `clearAndSetSemantics`,
 * in place of its parts' text: Compose gives TalkBack a merged node's parts as nodes of their
 * own, and TalkBack leaves out those scrolled off screen (ARCHITECTURE.md, Screen reader labels).
 */
@Composable
@ReadOnlyComposable
fun readAsOneLabel(vararg parts: String?): String =
    parts.filterNotNull().joinToString(stringResource(R.string.read_as_one_separator))
