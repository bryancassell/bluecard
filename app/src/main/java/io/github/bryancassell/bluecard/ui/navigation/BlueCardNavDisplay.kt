package io.github.bryancassell.bluecard.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
        // are animating, so a double tap can't press a control on the screen it opened. Keep
        // focus out of a screen that's leaving, so it can't pass to the next one.
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
            rememberIgnoreTouchesNavEntryDecorator(),
            rememberRefuseFocusWhileLeavingNavEntryDecorator(),
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
                    onOpenRank = { navigate(RankDetail(it)) },
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
                    // Once the scout discards an unsaved comment or sign-off.
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
    // A focus target around the pages, which takes input focus from a page that's left with it,
    // so the page shown next doesn't get it, and from a page that clears it. Otherwise, out of
    // touch mode, Compose clears the view's focus too, as the focused item leaves composition or
    // as a page clears focus. Android's View.clearFocus() then asks the view to take focus again,
    // and Compose gives it to the first item that can take it. As Badges was left, that was its
    // search field, which opened the keyboard, and TalkBack followed it (#285). After Save on a
    // requirement's page, it was the Completed checkbox (#297).
    val holder = remember { FocusRequester() }
    val page = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    // The holder can take focus only as it's given it here, and keeps it only until focus moves
    // on. Compose moves focus out to a parent that can take it on Back, so Back wouldn't leave
    // the page, and Tab would stop on the holder as it starts on a page or wraps around.
    var isTakingFocus by remember { mutableStateOf(false) }
    var isHolding by remember { mutableStateOf(false) }
    fun takeFocus() {
        isTakingFocus = true
        holder.requestFocus()
        isTakingFocus = false
    }
    // The page that's left is still composed as the next one comes in, with its item focused.
    LaunchedEffect(shownBackStack.last()) {
        if (hasFocus) takeFocus()
    }
    NavDisplay(
        sceneState = sceneState,
        // Nothing reports a back swipe to this state, so NavDisplay never plays one.
        navigationEventState = rememberNavigationEventState(sceneState),
        // Pages slide past the sides of their area. Clip them to it, so they don't draw under
        // a navigation bar or cutout at the side.
        modifier = modifier
            .clipToBounds()
            // Before Compose clears focus, as Save does to close the keyboard, it asks the focused
            // item's parents, from the innermost out. Moving focus to the holder, or cancelling
            // the clear when the holder already has it, leaves the view's focus alone. This is
            // around the holder, so it's asked while the holder has focus too, as after a page
            // opened from a field closes again. Tab clears focus as it wraps around, going Next or
            // Previous, and still reaches the page's first or last item.
            .focusProperties {
                onExit = {
                    if (requestedFocusDirection == FocusDirection.Exit) {
                        if (isHolding) cancelFocusChange() else takeFocus()
                    }
                }
            }
            .focusGroup()
            .focusRequester(holder)
            .onFocusChanged {
                hasFocus = it.hasFocus
                isHolding = it.isFocused
            }
            .focusProperties { canFocus = isTakingFocus || isHolding }
            // An arrow key moves from it to the page's first item, as on a phone where nothing
            // is focused: Android then asks the view to take focus going down. Compose would
            // look only beside the holder. Tab, Enter and D-pad center move into the page by
            // themselves.
            .onKeyEvent {
                isHolding && it.type == KeyEventType.KeyDown && it.key in ArrowKeys &&
                    page.requestFocus()
            }
            .focusTarget()
            .focusRequester(page)
            .focusGroup(),
        transitionSpec = {
            // Neither back stack is a page of the other, so they crossfade, as NavDisplay
            // does by default.
            if (replacesBackStack()) defaultTransitionSpec<NavKey>()(this) else openPage()
        },
        popTransitionSpec = { closePage() }
    )
}

private val ArrowKeys =
    setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)

/**
 * Whether the scenes show different back stacks, not one with pages added or removed, as when
 * Onboarding and the back stack replace each other.
 */
private fun AnimatedContentTransitionScope<Scene<NavKey>>.replacesBackStack(): Boolean =
    initialState.bottomEntry.contentKey != targetState.bottomEntry.contentKey

// In a SinglePaneScene, previousEntries are the entries under the one it shows.
private val Scene<NavKey>.bottomEntry: NavEntry<NavKey>
    get() = previousEntries.firstOrNull() ?: entries.first()
