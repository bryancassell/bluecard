package io.github.bryancassell.bluecard.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberNavigationEventState
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultTransitionSpec
import io.github.bryancassell.bluecard.ui.badge.BadgeDetailRoute
import io.github.bryancassell.bluecard.ui.badge.EditCounselorRoute
import io.github.bryancassell.bluecard.ui.badge.RequirementDetailRoute
import io.github.bryancassell.bluecard.ui.badge.TrackerEntryRoute
import io.github.bryancassell.bluecard.ui.badges.BadgesRoute
import io.github.bryancassell.bluecard.ui.data.DataManagementRoute
import io.github.bryancassell.bluecard.ui.home.HomeRoute
import io.github.bryancassell.bluecard.ui.onboarding.OnboardingRoute
import io.github.bryancassell.bluecard.ui.profile.EditProfileRoute
import io.github.bryancassell.bluecard.ui.rank.RankDetailRoute
import io.github.bryancassell.bluecard.ui.ranks.RanksRoute

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
    val backStack = rememberBackStack()
    // Deciding here, before anything is drawn, means the wrong screen never shows, and
    // Onboarding returns if the profile is ever missing. With only one entry, back leaves
    // the app.
    val shownBackStack = if (isSetUp) backStack else listOf(Onboarding)
    // Navigation reads this State when a screen is tapped, so a screen still animating out
    // after isSetUp changes navigates against what is shown now.
    val currentShownBackStack by rememberUpdatedState(shownBackStack)
    val drawnScreens = remember { DrawnScreens() }
    val unsavedChanges = remember { UnsavedChangesByPage() }
    // Screens look up the entry of one they'd open, to check it isn't still drawn.
    lateinit var entries: (NavKey) -> NavEntry<NavKey>
    val isDrawn = { key: NavKey -> entries(key) in drawnScreens }
    // Checks the back stack as it is now: two Backs can arrive before a frame turns Back
    // handling off, and the second mustn't empty the back stack. A page with unsaved changes
    // asks before Back discards them, rather than closing.
    val goBack: () -> Unit = {
        val shown = currentShownBackStack
        if (shown.size > 1 && !unsavedChanges.askToDiscard(entries(shown.last()))) {
            backStack.removeLastOrNull()
        }
    }
    // The only back handler here, added before the screens, so any a screen adds goes first.
    // NavDisplay is given no handler of its own, so a back swipe doesn't move the pages;
    // releasing it plays Back's slide.
    BackHandler(enabled = shownBackStack.size > 1, onBack = goBack)
    val decoratedEntries = rememberDecoratedNavEntries(
        backStack = shownBackStack,
        // Keep each entry's saved UI state, scope ViewModels to their entry so they are
        // cleared when the entry leaves the back stack, and ignore touches on screens that
        // are animating, so a double tap can't press a control on the screen it opened.
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
            rememberIgnoreTouchesNavEntryDecorator(),
            drawnScreens.decorator,
            unsavedChanges.decorator
        ),
        // Screens navigate with rememberNavigateFrom, so a double tap can't open a screen
        // twice, and a screen reader's click can't reopen one that's closing.
        entryProvider = entryProvider<NavKey> {
            entry<Onboarding> { OnboardingRoute() }
            entry<Home> { key ->
                val navigate =
                    rememberNavigateFrom(backStack, from = key, isDrawn) { currentShownBackStack }
                HomeRoute(
                    onOpenBadge = { navigate(BadgeDetail(it)) },
                    onOpenBadges = { navigate(Badges) },
                    onOpenRanks = { navigate(Ranks) },
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
            entry<Ranks> { key ->
                val navigate =
                    rememberNavigateFrom(backStack, from = key, isDrawn) { currentShownBackStack }
                RanksRoute(onOpenRank = { navigate(RankDetail(it)) })
            }
            entry<RankDetail> { key ->
                val navigate =
                    rememberNavigateFrom(backStack, from = key, isDrawn) { currentShownBackStack }
                RankDetailRoute(
                    rankId = key.rankId,
                    onOpenRequirement = { navigate(RequirementDetail(key.rankId, it)) }
                )
            }
            entry<EditCounselor> { key ->
                // Once saved or discarded, the page closes, unless the scout has already gone
                // back.
                EditCounselorRoute(
                    badgeId = key.badgeId,
                    onClose = { backStack.closeIfOnTop(key) }
                )
            }
            entry<RequirementDetail> { key ->
                val navigate =
                    rememberNavigateFrom(backStack, from = key, isDrawn) { currentShownBackStack }
                RequirementDetailRoute(
                    advancementId = key.advancementId,
                    number = key.number,
                    onOpenRequirement = { navigate(RequirementDetail(key.advancementId, it)) },
                    onOpenTrackerEntry = { entryId, rowNumber ->
                        navigate(
                            TrackerEntryDetail(key.advancementId, key.number, entryId, rowNumber)
                        )
                    },
                    onOpenBadge = { navigate(BadgeDetail(it)) },
                    // Once the scout discards an unsaved comment.
                    onClose = { backStack.closeIfOnTop(key) }
                )
            }
            entry<TrackerEntryDetail> { key ->
                TrackerEntryRoute(
                    advancementId = key.advancementId,
                    number = key.number,
                    entryId = key.entryId,
                    rowNumber = key.rowNumber,
                    // Only while it's on top, so it can't close a screen opened after it.
                    onClose = { backStack.closeIfOnTop(key) }
                )
            }
            entry<DataManagement> { key ->
                val navigate =
                    rememberNavigateFrom(backStack, from = key, isDrawn) { currentShownBackStack }
                DataManagementRoute(onEditProfile = { navigate(EditProfile) })
            }
            entry<EditProfile> { key ->
                // Once saved or discarded, the page closes, unless the scout has already gone
                // back.
                EditProfileRoute(onClose = { backStack.closeIfOnTop(key) })
            }
        }.also { entries = it }
    )
    val sceneState = rememberSceneState(
        entries = decoratedEntries,
        sceneStrategies = listOf(SinglePaneSceneStrategy()),
        onBack = goBack
    )
    NavDisplay(
        sceneState = sceneState,
        // Nothing reports a back swipe to this state, so NavDisplay never plays one.
        navigationEventState = rememberNavigationEventState(sceneState),
        // Pages slide past the sides of their area. Clip them to it, so they don't draw under
        // a navigation bar or cutout at the side.
        modifier = modifier.clipToBounds(),
        transitionSpec = {
            // Neither back stack is a page of the other, so they crossfade, as NavDisplay
            // does by default.
            if (replacesBackStack()) defaultTransitionSpec<NavKey>()(this) else openPage()
        },
        popTransitionSpec = { closePage() }
    )
}

/**
 * Whether the scenes show different back stacks, not one with pages added or removed, as when
 * Onboarding and the back stack replace each other.
 */
private fun AnimatedContentTransitionScope<Scene<NavKey>>.replacesBackStack(): Boolean =
    initialState.bottomEntry.contentKey != targetState.bottomEntry.contentKey

// In a SinglePaneScene, previousEntries are the entries under the one it shows.
private val Scene<NavKey>.bottomEntry: NavEntry<NavKey>
    get() = previousEntries.firstOrNull() ?: entries.first()
