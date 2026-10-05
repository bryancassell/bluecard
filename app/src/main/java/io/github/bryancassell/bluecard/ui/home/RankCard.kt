package io.github.bryancassell.bluecard.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.data.progress.RankStatus
import io.github.bryancassell.bluecard.ui.badges.percentDoneDescription
import io.github.bryancassell.bluecard.ui.ranks.RankListItem

/**
 * The scout's rank, a trail of every rank, and the rank in progress, which tapping the card
 * opens. It's in the theme's primary color, so it doesn't read as one of the merit badge cards or
 * rows below it. Once every rank is earned, it opens nothing.
 */
@Composable
fun RankCard(
    uiState: HomeUiState.Ready,
    onOpenRank: (rankId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val nextRank = uiState.nextRank
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary
        )
    ) {
        // Inside the card rather than on it, so the ripple keeps to the card's shape.
        val opensNextRank = if (nextRank == null) {
            Modifier
        } else {
            Modifier.clickable(
                onClickLabel = stringResource(R.string.ranks_open_rank),
                role = Role.Button,
                onClick = { onOpenRank(nextRank.id) }
            )
        }
        val percentDone = nextRank?.fractionDone?.let { percentDoneDescription(it) }
        Column(
            modifier = opensNextRank
                // Screen readers read the card as one heading, which they can jump to as to the
                // cards' titles below it, saying how much of the rank in progress is done as its
                // state, as its row on Ranks does.
                .semantics(mergeDescendants = true) {
                    heading()
                    percentDone?.let { stateDescription = it }
                }
                .padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.home_rank_title),
                style = MaterialTheme.typography.labelLarge,
                color = colors.inversePrimary
            )
            Text(
                text = uiState.rank?.name ?: stringResource(R.string.home_no_rank),
                style = MaterialTheme.typography.headlineMedium
            )
            RankTrail(uiState.ranks, Modifier.padding(top = 16.dp))
            // Seven names don't fit under the dots on a phone, so only the trail's ends have
            // one. Screen readers hear how many ranks are earned instead.
            Row(modifier = Modifier.fillMaxWidth().clearAndSetSemantics {}) {
                val labelStyle = MaterialTheme.typography.labelSmall
                // Half the row each, so a long name wraps rather than squeezing the other.
                Text(
                    text = uiState.ranks.first().name,
                    style = labelStyle,
                    color = colors.inversePrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = uiState.ranks.last().name,
                    style = labelStyle,
                    color = colors.inversePrimary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
            }
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (nextRank == null) {
                    Text(
                        text = stringResource(R.string.home_every_rank_earned),
                        style = MaterialTheme.typography.titleMedium
                    )
                } else {
                    Text(
                        text = stringResource(R.string.home_next_rank, nextRank.name),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(R.string.badges_in_progress),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.inversePrimary
                    )
                    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
                }
            }
        }
    }
}

/**
 * Every rank in a row, joined by a line: a filled dot for each rank earned, a larger dot filled
 * as far as the rank in progress is done, and a hollow dot for each rank after it. The line is
 * solid up to the rank in progress, or once every rank is earned, to the last, whose dot is
 * larger. The first and last dots sit at the trail's ends, over their names. Screen readers hear
 * how many ranks are earned.
 */
@Composable
private fun RankTrail(ranks: List<RankListItem>, modifier: Modifier = Modifier) {
    val earned = ranks.count { it.status == RankStatus.Earned }
    val description =
        pluralStringResource(R.plurals.home_ranks_earned, ranks.size, earned, ranks.size)
    val colors = MaterialTheme.colorScheme
    val done = colors.onPrimary
    val toGo = colors.inversePrimary
    val background = colors.primary
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(LargeDot)
            .semantics { text = AnnotatedString(description) }
    ) {
        val lineWidth = LineWidth.toPx()
        // Strokes are drawn centered on their circle, so each is inset to keep within its dot.
        val largeRadius = LargeDot.toPx() / 2
        val smallRadius = SmallDot.toPx() / 2
        val rightToLeft = layoutDirection == LayoutDirection.Rtl
        // A catalog with one rank has a single dot, at the start.
        val step = (size.width - 2 * largeRadius) / ranks.lastIndex.coerceAtLeast(1)
        fun dotCenter(index: Int): Offset {
            val x = largeRadius + index * step
            return Offset(if (rightToLeft) size.width - x else x, center.y)
        }
        val reached = ranks.indexOfFirst { it.status == RankStatus.InProgress }
            .takeIf { it >= 0 } ?: ranks.lastIndex
        drawLine(toGo, dotCenter(0), dotCenter(ranks.lastIndex), lineWidth)
        drawLine(done, dotCenter(0), dotCenter(reached), lineWidth)
        ranks.forEachIndexed { index, rank ->
            val dot = dotCenter(index)
            when (rank.status) {
                RankStatus.Earned -> {
                    drawCircle(done, if (index == reached) largeRadius else smallRadius, dot)
                }

                RankStatus.InProgress -> {
                    val radius = largeRadius - lineWidth / 2
                    drawCircle(background, radius, dot)
                    // Filled from the top as far as the rank is done, clockwise, or right to
                    // left counterclockwise, so the fill leads toward the ranks ahead.
                    rank.fractionDone?.let {
                        drawArc(
                            color = done,
                            startAngle = -90f,
                            sweepAngle = 360f * it * if (rightToLeft) -1 else 1,
                            useCenter = true,
                            topLeft = dot - Offset(radius, radius),
                            size = Size(2 * radius, 2 * radius)
                        )
                    }
                    drawCircle(done, radius, dot, style = Stroke(lineWidth))
                }

                RankStatus.NotEarned -> {
                    val radius = smallRadius - lineWidth / 2
                    drawCircle(background, radius, dot)
                    drawCircle(toGo, radius, dot, style = Stroke(lineWidth))
                }
            }
        }
    }
}

private val LargeDot = 22.dp
private val SmallDot = 14.dp
private val LineWidth = 2.dp
