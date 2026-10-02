package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation3.runtime.NavBackStack
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavKeysTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // One of each key, with the tracker row both ways it can be opened.
    private val everyKey = listOf(
        Onboarding,
        Home,
        Badges,
        BadgeDetail("camping"),
        DataManagement,
        RequirementDetail("camping", "4c"),
        TrackerEntryDetail("camping", "9a", entryId = 7),
        TrackerEntryDetail("camping", "4b", rowNumber = 3),
        EditCounselor("camping"),
        EditProfile
    )

    @Test
    fun everyKey_isInTheTest() {
        // From the compiled sealed interface, not its serializer, which leaves out a key that
        // isn't @Serializable. A new key fails here until it's added to everyKey.
        val keyClasses = BlueCardNavKey::class.java.permittedSubclasses.toSet()

        assertEquals(keyClasses, everyKey.map { it.javaClass }.toSet())
    }

    @Test
    fun backStack_holdingEveryKey_isRestored() {
        val tester = StateRestorationTester(composeTestRule)
        lateinit var backStack: NavBackStack<BlueCardNavKey>
        tester.setContent { backStack = rememberBackStack() }
        // Changed after it's remembered, so only the saved state can bring these keys back.
        composeTestRule.runOnIdle { backStack.addAll(everyKey) }

        // Saves to a Parcel and restores from it, as after process death.
        tester.emulateSavedInstanceStateRestore()

        composeTestRule.runOnIdle { assertEquals(listOf(Home) + everyKey, backStack.toList()) }
    }
}
