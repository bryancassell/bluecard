package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
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

    private val lifecycleOwner = object : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private val backStack = mutableListOf<NavKey>(Home, Badges)

    /** Navigation from the Badges screen, which is on top of the back stack. */
    private fun navigateFromBadges(): (NavKey) -> Unit {
        lateinit var navigate: (NavKey) -> Unit
        composeTestRule.setContent {
            navigate = rememberNavigateFrom(backStack, from = Badges, lifecycleOwner)
        }
        return navigate
    }

    @Test
    fun resumedAndOnTop_navigates() {
        lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED

        navigateFromBadges()(BadgeDetail("camping"))

        assertEquals(listOf(Home, Badges, BadgeDetail("camping")), backStack)
    }

    @Test
    fun started_doesNothing() {
        // As NavDisplay holds a screen while it animates in or out.
        lifecycleOwner.registry.currentState = Lifecycle.State.STARTED

        navigateFromBadges()(BadgeDetail("camping"))

        assertEquals(listOf(Home, Badges), backStack)
    }

    @Test
    fun secondCall_beforeScreenChanges_doesNothing() {
        // Both taps land before NavDisplay moves the screen out of RESUMED.
        lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED
        val navigate = navigateFromBadges()

        navigate(BadgeDetail("camping"))
        navigate(BadgeDetail("chess"))

        assertEquals(listOf(Home, Badges, BadgeDetail("camping")), backStack)
    }

    @Test
    fun checksWhenCalled() {
        lifecycleOwner.registry.currentState = Lifecycle.State.STARTED
        val navigate = navigateFromBadges()

        navigate(BadgeDetail("dropped"))
        lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED
        navigate(BadgeDetail("camping"))

        assertEquals(listOf(Home, Badges, BadgeDetail("camping")), backStack)
    }
}
