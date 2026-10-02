package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
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
        // The sealed serializer lists every key's serial name, so a new key fails here until
        // it's added to everyKey.
        val keys = BlueCardNavKey.serializer().descriptor.getElementDescriptor(1)
        val keyNames = (0 until keys.elementsCount).map { keys.getElementName(it) }

        assertEquals(keyNames.toSet(), everyKey.map { it::class.qualifiedName }.toSet())
    }

    @Test
    fun backStack_holdingEveryKey_isRestored() {
        val tester = StateRestorationTester(composeTestRule)
        lateinit var backStack: NavBackStack<NavKey>
        tester.setContent {
            backStack = rememberNavBackStack(BackStackSavedStateConfiguration, Home)
        }
        // Changed after it's remembered, so only the saved state can bring these keys back.
        composeTestRule.runOnIdle { backStack.addAll(everyKey) }

        // Saves to a Parcel and restores from it, as after process death.
        tester.emulateSavedInstanceStateRestore()

        composeTestRule.runOnIdle { assertEquals(listOf(Home) + everyKey, backStack.toList()) }
    }
}
