package io.github.bryancassell.bluecard.ui.badges

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.bryancassell.bluecard.R
import kotlin.math.roundToInt

/**
 * How much of a badge in progress is done, [fractionDone] from 0 to 1, as a bar as wide as its
 * space: on the badge's row on Badges and Home, and on Badge detail.
 */
@Composable
fun BadgeProgressBar(fractionDone: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { fractionDone },
        // Material's default track color is almost the page's, so the bar's full length
        // wouldn't show.
        trackColor = MaterialTheme.colorScheme.outlineVariant,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * How much of a badge in progress is done, [fractionDone] from 0 to 1, as screen readers hear
 * it: "40% done". As for Compose's own progress bars, it's 0% only when nothing is done, so a
 * little progress isn't heard as none, and never 100%, since a badge in progress has something
 * left.
 */
@Composable
fun percentDoneDescription(fractionDone: Float): String {
    val percent = if (fractionDone == 0f) 0 else (fractionDone * 100).roundToInt().coerceIn(1, 99)
    return stringResource(R.string.badges_percent_done, percent)
}
