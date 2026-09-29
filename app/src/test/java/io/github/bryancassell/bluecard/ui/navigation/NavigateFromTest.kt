package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigateFromTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val backStack = mutableListOf<NavKey>(Home, Badges)

    // What NavDisplay shows: the back stack, or Onboarding in its place.
    private var shownBackStack: List<NavKey> = backStack

    /** Navigation from the Badges screen. */
    private fun navigateFromBadges(): (NavKey) -> Unit {
        lateinit var navigate: (NavKey) -> Unit
        composeTestRule.setContent {
            navigate = rememberNavigateFrom(backStack, from = Badges) { shownBackStack }
        }
        return navigate
    }

    @Test
    fun onTop_navigates() {
        navigateFromBadges()(BadgeDetail("camping"))

        assertEquals(listOf(Home, Badges, BadgeDetail("camping")), backStack)
    }

    @Test
    fun secondCall_afterScreenChanged_doesNothing() {
        // As for a double tap: the first tap put Badge detail on top, so the second tap,
        // on the Badges screen that is leaving, is ignored.
        val navigate = navigateFromBadges()

        navigate(BadgeDetail("camping"))
        navigate(BadgeDetail("chess"))

        assertEquals(listOf(Home, Badges, BadgeDetail("camping")), backStack)
    }

    @Test
    fun notShown_doesNothing() {
        // As when the profile is removed: NavDisplay shows Onboarding in place of the back
        // stack, while Badges is still on top of it.
        shownBackStack = listOf(Onboarding)

        navigateFromBadges()(BadgeDetail("camping"))

        assertEquals(listOf(Home, Badges), backStack)
    }

    @Test
    fun checksWhatIsShownWhenCalled() {
        shownBackStack = listOf(Onboarding)
        val navigate = navigateFromBadges()

        navigate(BadgeDetail("dropped"))
        shownBackStack = backStack
        navigate(BadgeDetail("camping"))

        assertEquals(listOf(Home, Badges, BadgeDetail("camping")), backStack)
    }
}
