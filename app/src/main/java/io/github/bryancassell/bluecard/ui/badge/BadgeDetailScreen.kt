package io.github.bryancassell.bluecard.ui.badge

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.ui.LoadFailedMessage
import io.github.bryancassell.bluecard.ui.ScreenMessage
import io.github.bryancassell.bluecard.ui.badges.eagleRequirementLabel
import io.github.bryancassell.bluecard.ui.badges.rememberBadgeNameListFormatter
import io.github.bryancassell.bluecard.ui.rememberStartOtherApp

/** Connects the Badge detail screen to its ViewModel. */
@Composable
fun BadgeDetailRoute(
    badgeId: String,
    onOpenRequirement: (number: String) -> Unit,
    onEditCounselor: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BadgeDetailViewModel =
        hiltViewModel<BadgeDetailViewModel, BadgeDetailViewModel.Factory> { it.create(badgeId) }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BadgeDetailScreen(
        uiState = uiState,
        onOpenRequirement = onOpenRequirement,
        onEditCounselor = onEditCounselor,
        modifier = modifier
    )
}

/**
 * A badge's summary, whether it's Eagle-required, a link to its official page, the scout's
 * merit badge counselor, and its top-level requirements. Each requirement opens its own page,
 * where the scout marks it complete, and the counselor is entered on a page of its own, which
 * keeps this one short.
 */
@Composable
fun BadgeDetailScreen(
    uiState: BadgeDetailUiState,
    onOpenRequirement: (number: String) -> Unit,
    onEditCounselor: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        BadgeDetailUiState.Loading -> LoadingIndicator(modifier)

        BadgeDetailUiState.LoadFailed -> LoadFailedMessage(modifier)

        BadgeDetailUiState.Unavailable ->
            ScreenMessage(stringResource(R.string.requirements_unavailable), modifier)

        is BadgeDetailUiState.Ready ->
            BadgeDetails(uiState, onOpenRequirement, onEditCounselor, modifier)
    }
}

/** Test tag of the official requirements link's icon, which has no semantics of its own. */
internal const val OFFICIAL_LINK_ICON_TAG = "officialLinkIcon"

@Composable
private fun BadgeDetails(
    uiState: BadgeDetailUiState.Ready,
    onOpenRequirement: (number: String) -> Unit,
    onEditCounselor: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Opens the official page in the browser. The counselor's phone and email share the
    // function, so quick taps on any of them open one app, once.
    val startOtherApp = rememberStartOtherApp()
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = uiState.name,
                style = MaterialTheme.typography.headlineMedium,
                // Lets screen reader users jump to it.
                modifier = Modifier.semantics { heading() }
            )
            uiState.eagle?.let {
                Text(
                    text = eagleRequirementLabel(it, rememberBadgeNameListFormatter()),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(text = uiState.summary, style = MaterialTheme.typography.bodyLarge)
            val officialPage = Intent(Intent.ACTION_VIEW, uiState.officialUrl.toUri())
            // Shown when no app can open web links, as when parental controls block the browser.
            val noBrowser = stringResource(R.string.badge_detail_no_browser)
            val openInBrowser = stringResource(R.string.badge_detail_open_in_browser)
            // Outlined, with an "open in new" icon, so it stands out and says it leaves the app.
            // The theme's outline color, rather than Material's lighter default, keeps the outline
            // visible on the tinted background, and the label is primary blue, like a link.
            OutlinedButton(
                onClick = { startOtherApp(officialPage, noBrowser) },
                // The button keeps its own click action, with this label.
                modifier = Modifier.semantics { onClick(label = openInBrowser, action = null) },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                border = ButtonDefaults.outlinedButtonBorder()
                    .copy(brush = SolidColor(MaterialTheme.colorScheme.outline)),
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
        CounselorSection(uiState.counselor, onEdit = onEditCounselor, startOtherApp = startOtherApp)
        Text(
            text = stringResource(R.string.badge_detail_requirements),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { heading() }
        )
        uiState.requirements.forEach {
            RequirementRow(item = it, onOpen = onOpenRequirement)
        }
    }
}
