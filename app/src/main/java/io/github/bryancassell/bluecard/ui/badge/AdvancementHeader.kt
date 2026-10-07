package io.github.bryancassell.bluecard.ui.badge

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.ui.OtherAppStarter
import io.github.bryancassell.bluecard.ui.badges.BadgeProgressBar
import io.github.bryancassell.bluecard.ui.badges.percentDoneDescription

/** Test tag of the official requirements link's icon, which has no semantics of its own. */
internal const val OFFICIAL_LINK_ICON_TAG = "officialLinkIcon"

/**
 * The top of a badge's or rank's page: its [name], a bar for how much is done, [fractionDone]
 * from 0 to 1, if it shows one, a [tag] such as whether it's Eagle-required, its [summary], and a
 * link to its official page, [officialUrl], which opens in the browser with [startOtherApp].
 * Screen readers hear only how much the bar says is done: the page's status card says whether
 * it's in progress, so the bar doesn't say it too, as it does on the item's row in a list.
 */
@Composable
fun AdvancementHeader(
    name: String,
    fractionDone: Float?,
    summary: String,
    officialUrl: String,
    startOtherApp: OtherAppStarter,
    modifier: Modifier = Modifier,
    tag: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.headlineMedium,
            // Lets screen reader users jump to it.
            modifier = Modifier.semantics { heading() }
        )
        fractionDone?.let {
            val percentDone = percentDoneDescription(it)
            BadgeProgressBar(
                fractionDone = it,
                modifier = Modifier.semantics { stateDescription = percentDone }
            )
        }
        tag?.invoke()
        Text(text = summary, style = MaterialTheme.typography.bodyLarge)
        OfficialPageButton(officialUrl, startOtherApp)
    }
}

/** Opens [officialUrl], a badge's or rank's official page, in the browser. */
@Composable
private fun OfficialPageButton(officialUrl: String, startOtherApp: OtherAppStarter) {
    // ACTION_VIEW, as Compose's UriHandler uses, but started by the screen's OtherAppStarter, so
    // it handles a double tap and "no app" as the page's other links to apps do, such as a
    // counselor's phone and email.
    val officialPage = Intent(Intent.ACTION_VIEW, officialUrl.toUri())
    // Shown when no app can open web links, as when parental controls block the browser.
    val noBrowser = stringResource(R.string.badge_detail_no_browser)
    val openInBrowser = stringResource(R.string.badge_detail_open_in_browser)
    // Outlined, with an "open in new" icon, so it stands out and says it leaves the app.
    OutlinedButton(
        onClick = { startOtherApp(officialPage, noBrowser) },
        // The button keeps its own click action, with this label.
        modifier = Modifier.semantics { onClick(label = openInBrowser, action = null) },
        colors = detailOutlinedButtonColors(),
        border = detailOutlinedButtonBorder(),
        // Material's padding for an icon before the label, flipped for one after it.
        contentPadding = with(ButtonDefaults.ButtonWithIconContentPadding) {
            PaddingValues(
                start = calculateEndPadding(LayoutDirection.Ltr),
                top = calculateTopPadding(),
                end = calculateStartPadding(LayoutDirection.Ltr),
                bottom = calculateBottomPadding()
            )
        }
    ) {
        Text(text = stringResource(R.string.badge_detail_official_page))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Icon(
            painterResource(R.drawable.ic_open_in_new),
            contentDescription = null,
            modifier = Modifier
                .size(ButtonDefaults.IconSize)
                .testTag(OFFICIAL_LINK_ICON_TAG)
        )
    }
}

/**
 * Colors of a badge's or rank's outlined buttons, such as the official link and the report
 * buttons: the label is primary blue, like a link.
 */
@Composable
internal fun detailOutlinedButtonColors(): ButtonColors =
    ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)

/**
 * Border of a badge's or rank's outlined buttons, in the theme's outline color rather than
 * Material's lighter default, which keeps it visible on the tinted background.
 */
@Composable
internal fun detailOutlinedButtonBorder(): BorderStroke = ButtonDefaults.outlinedButtonBorder()
    .copy(brush = SolidColor(MaterialTheme.colorScheme.outline))
