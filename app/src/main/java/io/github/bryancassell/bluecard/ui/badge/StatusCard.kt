package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** Test tag of a badge's or rank's status card, for its screenshot tests. */
internal const val STATUS_CARD_TAG = "statusCard"

/**
 * Where a badge or rank stands, in a card under its official link, with what the scout can do
 * about it: mark it done on a prior date, change or remove that date, or share and save its
 * report once it's done. The card sets these apart from the rest of the page, so its buttons read
 * as things to tap ([#247](https://github.com/bryancassell/bluecard/issues/247)). It's white, the
 * theme's lightest surface, so a tonal button stands out on it.
 *
 * Each line pads its own sides, as on the page, so a text button's text lines up with the card's
 * text. Its last line is always a button, whose touch area leaves room under it, so the card
 * leaves less.
 */
@Composable
fun StatusCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp)
            .fillMaxWidth()
            .testTag(STATUS_CARD_TAG),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        )
    ) {
        Column(Modifier.padding(top = 16.dp, bottom = 12.dp), content = content)
    }
}

/** The style of a status card's first line, also for a date there the scout can change. */
val statusLineStyle: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.typography.titleMedium

/** A status card's first line: where the badge or rank stands, such as "In progress". */
@Composable
fun StatusLine(text: String) {
    Text(text = text, style = statusLineStyle, modifier = Modifier.padding(horizontal = 16.dp))
}

/**
 * A status card's lines while the badge or rank isn't done: its [status], the [prompt] asking
 * whether the scout has already done it, if there is one, and a button labeled [markText] that
 * marks it done on a date the scout picks, up to [today], without recording its requirements
 * ([onMark]). The picker opens at [initial], if given, or at today.
 */
@Composable
fun MarkDoneLines(
    status: String,
    prompt: String?,
    markText: String,
    initial: LocalDate?,
    today: () -> LocalDate,
    onMark: (date: LocalDate) -> Unit
) {
    StatusLine(status)
    prompt?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp)
        )
    }
    MarkOnDateButton(
        text = markText,
        initial = initial,
        today = today,
        onPick = onMark,
        // With its touch area, 12dp under the text above.
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp)
    )
}
