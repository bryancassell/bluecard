package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.bryancassell.bluecard.ui.badges.BadgesScreen
import io.github.bryancassell.bluecard.ui.home.HomeRoute
import io.github.bryancassell.bluecard.ui.onboarding.OnboardingRoute

/**
 * The app's navigation root: shows the screen on top of the back stack.
 *
 * [startDestination] is only read the first time; after that the saved back stack wins.
 */
@Composable
fun BlueCardNavDisplay(startDestination: NavKey, modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(startDestination)
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        // Keep each entry's saved UI state, and scope ViewModels to their entry so they
        // are cleared when the entry leaves the back stack.
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            // Replace Onboarding with Home, so back from Home leaves the app.
            entry<Onboarding> {
                OnboardingRoute(onSaved = {
                    backStack.add(Home)
                    backStack.remove(Onboarding)
                })
            }
            entry<Home> { HomeRoute(onOpenBadges = { backStack.add(Badges) }) }
            entry<Badges> { BadgesScreen() }
        }
    )
}
