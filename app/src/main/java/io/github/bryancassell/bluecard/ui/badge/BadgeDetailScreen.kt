package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.bryancassell.bluecard.R

/** Placeholder for a badge's detail page. Shows the ID of the badge it was opened for. */
@Composable
fun BadgeDetailScreen(badgeId: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(text = stringResource(R.string.badge_detail_title))
        Text(text = badgeId)
    }
}
