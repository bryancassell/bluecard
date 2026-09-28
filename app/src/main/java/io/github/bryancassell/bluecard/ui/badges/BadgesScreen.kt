package io.github.bryancassell.bluecard.ui.badges

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.bryancassell.bluecard.R

/** Placeholder for browsing and searching merit badges. */
@Composable
fun BadgesScreen(modifier: Modifier = Modifier) {
    Text(text = stringResource(R.string.badges_title), modifier = modifier.padding(16.dp))
}
