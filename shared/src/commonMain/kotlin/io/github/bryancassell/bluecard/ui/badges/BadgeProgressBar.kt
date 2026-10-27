package io.github.bryancassell.bluecard.ui.badges

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.badges_percent_done
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * How much of a badge or rank is done, [fractionDone] from 0 to 1, as a bar as wide as its
 * space: on the row of a badge or rank in a list, and on Badge detail and Rank detail.
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
 * How much of a badge or rank is done, [fractionDone] from 0 to 1, as screen readers hear it:
 * "40% done". As for Compose's own progress bars, it's 0% only when nothing is done, so a little
 * progress isn't heard as none, and 100% only when everything is, as for a rank waiting on the
 * rank below it. A badge in progress has something left.
 */
@Composable
fun percentDoneDescription(fractionDone: Float): String {
    val percent = when (fractionDone) {
        0f -> 0
        1f -> 100
        else -> (fractionDone * 100).roundToInt().coerceIn(1, 99)
    }
    return stringResource(Res.string.badges_percent_done, percent)
}
