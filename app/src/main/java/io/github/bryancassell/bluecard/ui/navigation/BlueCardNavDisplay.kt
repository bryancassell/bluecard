package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.bryancassell.bluecard.ui.badge.BadgeDetailScreen
import io.github.bryancassell.bluecard.ui.badges.BadgesRoute
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
    NavDisplay(
        // Deciding here, before anything is drawn, means the wrong screen never shows,
        // and Onboarding returns if the profile is ever missing. With only one entry,
        // back leaves the app.
        backStack = if (isSetUp) backStack else listOf(Onboarding),
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        // Keep each entry's saved UI state, and scope ViewModels to their entry so they
        // are cleared when the entry leaves the back stack.
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<Onboarding> { OnboardingRoute() }
            entry<Home> { HomeRoute(onOpenBadges = { backStack.add(Badges) }) }
            entry<Badges> { BadgesRoute(onOpenBadge = { backStack.add(BadgeDetail(it)) }) }
            entry<BadgeDetail> { BadgeDetailScreen() }
        }
    )
}
