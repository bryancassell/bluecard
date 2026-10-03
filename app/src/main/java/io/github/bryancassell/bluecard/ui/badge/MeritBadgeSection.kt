package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.data.progress.EarnedBadge
import io.github.bryancassell.bluecard.data.progress.MeritBadgeCredit
import io.github.bryancassell.bluecard.ui.badges.AdvancementRow

// A rank's requirement that asks for merit badges, on its page and in its row.

/** "4 of 6 merit badges". */
@Composable
@ReadOnlyComposable
fun meritBadgesCountLabel(credit: MeritBadgeCredit): String = pluralStringResource(
    R.plurals.requirement_merit_badges_count,
    credit.needed.total,
    credit.completed,
    credit.needed.total
)

/** "2 of 4 Eagle-required". */
@Composable
@ReadOnlyComposable
fun eagleRequiredCountLabel(credit: MeritBadgeCredit): String = pluralStringResource(
    R.plurals.requirement_eagle_required_count,
    credit.needed.eagleRequired,
    credit.eagleRequired,
    credit.needed.eagleRequired
)

/**
 * A rank's requirement that asks for merit badges, on its page: a heading, how many badges the
 * scout has completed and how many of them count as Eagle-required, how many more they need,
 * then the badges they've completed, in [badges]' order, each of which opens its page
 * ([onOpenBadge]) and says whether it counts as Eagle-required toward the requirement.
 */
@Composable
fun MeritBadgeSection(
    credit: MeritBadgeCredit,
    badges: List<EarnedBadge>,
    onOpenBadge: (badgeId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            text = stringResource(R.string.requirement_merit_badges_title),
            style = MaterialTheme.typography.titleMedium,
            // Lets screen reader users jump to it.
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { heading() }
        )
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
            val lines = listOfNotNull(
                meritBadgesCountLabel(credit),
                eagleRequiredCountLabel(credit),
                moreNeededLabel(credit)
            )
            lines.forEach {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val formatter = rememberCompletionDateFormatter()
        badges.forEach { earned ->
            // Keyed, so a row's state stays with its badge when one before it is cleared.
            key(earned.badge.id) {
                AdvancementRow(
                    name = earned.badge.name,
                    detail = earned.completedOn?.let {
                        stringResource(
                            R.string.requirement_merit_badge_completed_on,
                            formatter.format(it)
                        )
                    } ?: stringResource(R.string.requirement_merit_badge_completed),
                    status = if (earned.countsAsEagleRequired(credit.needed)) {
                        stringResource(R.string.requirement_merit_badge_eagle_required)
                    } else {
                        null
                    },
                    fractionDone = null,
                    onClickLabel = stringResource(R.string.badges_open_badge),
                    onClick = { onOpenBadge(earned.badge.id) }
                )
            }
        }
    }
}

/**
 * "Needs 2 more merit badges", "Needs 2 more Eagle-required merit badges", or "Needs 3 more merit
 * badges, 1 of them Eagle-required", or null once the scout has enough.
 */
@Composable
@ReadOnlyComposable
private fun moreNeededLabel(credit: MeritBadgeCredit): String? {
    val more = credit.moreNeeded
    val moreEagleRequired = credit.moreEagleRequiredNeeded
    return when {
        more == 0 -> null

        moreEagleRequired == 0 ->
            pluralStringResource(R.plurals.requirement_merit_badges_needed, more, more)

        moreEagleRequired == more ->
            pluralStringResource(R.plurals.requirement_eagle_required_badges_needed, more, more)

        else -> pluralStringResource(
            R.plurals.requirement_merit_badges_needed_some_eagle_required,
            more,
            more,
            moreEagleRequired
        )
    }
}
