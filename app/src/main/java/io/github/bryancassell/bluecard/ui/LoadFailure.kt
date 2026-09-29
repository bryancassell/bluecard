package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.bryancassell.bluecard.R
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * Emits [failed] in place of an [IOException], which repositories throw when stored data
 * can't be read. Any other exception is a bug, so it still crashes the app and reaches crash
 * reports instead of hiding behind [LoadFailedMessage] (see ARCHITECTURE.md, UI layer).
 */
fun <T> Flow<T>.catchLoadFailure(failed: T): Flow<T> =
    catch { if (it is IOException) emit(failed) else throw it }

/** Shown in place of a screen's content when its data couldn't be loaded. */
@Composable
fun LoadFailedMessage(modifier: Modifier = Modifier) {
    Text(text = stringResource(R.string.load_failed), modifier = modifier.padding(16.dp))
}
