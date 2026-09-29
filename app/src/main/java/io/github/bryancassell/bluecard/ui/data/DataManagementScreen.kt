package io.github.bryancassell.bluecard.ui.data

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.bryancassell.bluecard.R

/** Placeholder for clearing all progress, export and import. */
@Composable
fun DataManagementScreen(modifier: Modifier = Modifier) {
    Text(text = stringResource(R.string.data_management_title), modifier = modifier.padding(16.dp))
}
