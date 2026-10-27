package io.github.bryancassell.bluecard.ui.ranks

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.bryancassell.bluecard.data.progress.RankStatus
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.badges_in_progress
import io.github.bryancassell.bluecard.resources.ranks_earned
import io.github.bryancassell.bluecard.resources.ranks_open_rank
import io.github.bryancassell.bluecard.ui.badges.AdvancementRow
import org.jetbrains.compose.resources.stringResource

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
            RankStatus.InProgress -> stringResource(Res.string.badges_in_progress)
            RankStatus.Earned -> stringResource(Res.string.ranks_earned)
        },
        fractionDone = rank.fractionDone,
        onClickLabel = stringResource(Res.string.ranks_open_rank),
        onClick = onClick,
        modifier = modifier
    )
}
