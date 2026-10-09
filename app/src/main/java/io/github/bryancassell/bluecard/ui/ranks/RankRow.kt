package io.github.bryancassell.bluecard.ui.ranks

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.data.progress.RankStatus
import io.github.bryancassell.bluecard.ui.badges.AdvancementRow

/**
 * A rank's row on Ranks. It's the same row as a badge's, so ranks look familiar: the rank's name
 * and status, with a bar for how much is done while it shows one. Its status says "Earned" where a
 * badge's says "Completed", as scouts say of a rank.
 */
@Composable
fun RankRow(rank: RankListItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AdvancementRow(
        name = rank.name,
        detail = null,
        status = when (rank.status) {
            RankStatus.NotEarned -> null
            RankStatus.InProgress -> stringResource(R.string.badges_in_progress)
            RankStatus.Earned -> stringResource(R.string.ranks_earned)
        },
        fractionDone = rank.fractionDone,
        onClickLabel = stringResource(R.string.ranks_open_rank),
        onClick = onClick,
        modifier = modifier
    )
}
