package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.bryancassell.bluecard.ui.badge.BadgeDetailRoute
import io.github.bryancassell.bluecard.ui.badge.EditCounselorRoute
import io.github.bryancassell.bluecard.ui.badge.RequirementDetailRoute
import io.github.bryancassell.bluecard.ui.badge.TrackerEntryRoute
import io.github.bryancassell.bluecard.ui.badges.BadgesRoute
import io.github.bryancassell.bluecard.ui.data.DataManagementScreen
import io.github.bryancassell.bluecard.ui.home.HomeRoute
import io.github.bryancassell.bluecard.ui.onboarding.OnboardingRoute

/**
 * The app's navigation root: shows the screen on top of the back stack.
 *
 * Home is the fixed start destination. Until the scout's profile is saved ([isSetUp]),
 * Onboarding is shown in place of the back stack; the navigation principles say one-time
 * setup screens "should not be considered start destinations":
 * https://developer.android.com/guide/navigation/principles#fixed_start_destination
 */
@Composable
fun BlueCardNavDisplay(isSetUp: Boolean, modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(Home)
    // Deciding here, before anything is drawn, means the wrong screen never shows, and
    // Onboarding returns if the profile is ever missing. With only one entry, back leaves
    // the app.
    val shownBackStack = if (isSetUp) backStack else listOf(Onboarding)
    // Navigation reads this State when a screen is tapped, so a screen still animating out
    // after isSetUp changes navigates against what is shown now.
    val currentShownBackStack by rememberUpdatedState(shownBackStack)
    val drawnScreens = remember { DrawnScreens() }
    // Screens look up the entry of one they'd open, to check it isn't still drawn.
    lateinit var entries: (NavKey) -> NavEntry<NavKey>
    val isDrawn = { key: NavKey -> entries(key) in drawnScreens }
    NavDisplay(
        backStack = shownBackStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        // Keep each entry's saved UI state, scope ViewModels to their entry so they are
        // cleared when the entry leaves the back stack, and ignore touches on screens that
        // are animating, so a double tap can't press a control on the screen it opened.
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
            rememberIgnoreTouchesNavEntryDecorator(),
            drawnScreens.decorator
        ),
        // Screens navigate with rememberNavigateFrom, so a double tap can't open a screen
        // twice, and a screen reader's click can't reopen one that's closing.
        entryProvider = entryProvider {
            entry<Onboarding> { OnboardingRoute() }
            entry<Home> { key ->
                val navigate =
                    rememberNavigateFrom(backStack, from = key, isDrawn) { currentShownBackStack }
                HomeRoute(
                    onOpenBadges = { navigate(Badges) },
                    onOpenDataManagement = { navigate(DataManagement) }
                )
            }
            entry<Badges> { key ->
                val navigate =
                    rememberNavigateFrom(backStack, from = key, isDrawn) { currentShownBackStack }
                BadgesRoute(onOpenBadge = { navigate(BadgeDetail(it)) })
            }
            entry<BadgeDetail> { key ->
                val navigate =
                    rememberNavigateFrom(backStack, from = key, isDrawn) { currentShownBackStack }
                BadgeDetailRoute(
                    badgeId = key.badgeId,
                    onOpenRequirement = { navigate(RequirementDetail(key.badgeId, it)) },
                    onEditCounselor = { navigate(EditCounselor(key.badgeId)) }
                )
            }
            entry<EditCounselor> { key ->
                // Once saved, the page closes, unless the scout has already gone back.
                EditCounselorRoute(
                    badgeId = key.badgeId,
                    onSaved = { backStack.closeIfOnTop(key) }
                )
            }
            entry<RequirementDetail> { key ->
                val navigate =
                    rememberNavigateFrom(backStack, from = key, isDrawn) { currentShownBackStack }
                RequirementDetailRoute(
                    badgeId = key.badgeId,
                    number = key.number,
                    onOpenRequirement = { navigate(RequirementDetail(key.badgeId, it)) },
                    onOpenTrackerEntry = { entryId, rowNumber ->
                        navigate(TrackerEntryDetail(key.badgeId, key.number, entryId, rowNumber))
                    }
                )
            }
            entry<TrackerEntryDetail> { key ->
                TrackerEntryRoute(
                    badgeId = key.badgeId,
                    number = key.number,
                    entryId = key.entryId,
                    rowNumber = key.rowNumber,
                    // Only while it's on top, so it can't close a screen opened after it.
                    onClose = { backStack.closeIfOnTop(key) }
                )
            }
            entry<DataManagement> { DataManagementScreen() }
        }.also { entries = it }
    )
}
