package io.github.bryancassell.bluecard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.github.bryancassell.bluecard.data.profile.FakeProfileRepository
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.di.ProfileModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Local UI test: Robolectric launches the activity on the JVM with Hilt's test
 * application, so test modules (such as TestDispatchersModule) replace real ones, and
 * this class swaps the profile for a fake. Each test starts with no saved profile, as on
 * a fresh install.
 */
@HiltAndroidTest
@UninstallModules(ProfileModule::class)
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    // Hilt must set up its components before the activity launches.
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    // An empty rule, so tests can save a profile before launching the activity.
    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    private val fakeProfileRepository = FakeProfileRepository()

    @BindValue
    @JvmField
    val profileRepository: ProfileRepository = fakeProfileRepository

    private lateinit var scenario: ActivityScenario<MainActivity>

    @After
    fun tearDown() {
        // A test can fail before it launches the activity.
        if (::scenario.isInitialized) scenario.close()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun launchWithProfile() {
        runBlocking { profileRepository.saveProfile(Profile("Alex Scout", "123")) }
        launch()
    }

    private fun field(label: String) = composeTestRule.onNode(hasSetTextAction() and hasText(label))

    private fun completeOnboarding() {
        field("Name").performTextInput("Alex Scout")
        field("Unit number").performTextInput("123")
        composeTestRule.onNodeWithText("Get started").performClick()
    }

    private fun assertActivityFinishing() {
        // Robolectric doesn't move a finishing activity on to DESTROYED by itself.
        var isFinishing = false
        scenario.onActivity { isFinishing = it.isFinishing }
        assertTrue(isFinishing)
    }

    @Test
    fun firstLaunch_showsOnboarding() {
        launch()

        composeTestRule.onNodeWithText("Welcome to BlueCard").assertIsDisplayed()
        composeTestRule.onNodeWithText("Home").assertDoesNotExist()
    }

    @Test
    fun back_onOnboarding_leavesApp() {
        launch()
        composeTestRule.onNodeWithText("Welcome to BlueCard").assertIsDisplayed()

        pressBackUnconditionally()

        assertActivityFinishing()
    }

    @Test
    fun completingOnboarding_savesProfileAndShowsHome() {
        launch()

        completeOnboarding()

        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        composeTestRule.onNodeWithText("Welcome to BlueCard").assertDoesNotExist()
        assertEquals(
            Profile("Alex Scout", "123"),
            runBlocking { profileRepository.observeProfile().first() }
        )
    }

    @Test
    fun back_fromHomeAfterOnboarding_leavesApp() {
        launch()
        completeOnboarding()
        // Back on Onboarding would also leave the app, so check that Home is showing first.
        composeTestRule.onNodeWithText("Home").assertIsDisplayed()

        // Home is the start destination, so there is nothing to go back to.
        pressBackUnconditionally()

        assertActivityFinishing()
    }

    @Test
    fun profileSaved_whileOnboardingShows_movesToHome() {
        launch()
        composeTestRule.onNodeWithText("Welcome to BlueCard").assertIsDisplayed()

        // As when the app returns after a save that finished in the background.
        runBlocking { profileRepository.saveProfile(Profile("Alex Scout", "123")) }

        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        composeTestRule.onNodeWithText("Welcome to BlueCard").assertDoesNotExist()
    }

    @Test
    fun launchWithProfile_goesStraightToHome() {
        launchWithProfile()

        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        composeTestRule.onNodeWithText("Welcome to BlueCard").assertDoesNotExist()
    }

    @Test
    fun profileRemoved_showsOnboardingAgain() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Merit badges").performClick()
        composeTestRule.onNodeWithText("Merit badges").assertIsDisplayed()

        fakeProfileRepository.removeProfile()

        composeTestRule.onNodeWithText("Welcome to BlueCard").assertIsDisplayed()
        composeTestRule.onNodeWithText("Merit badges").assertDoesNotExist()
    }

    @Test
    fun openBadges_showsBadges() {
        launchWithProfile()

        composeTestRule.onNodeWithText("Merit badges").performClick()

        composeTestRule.onNodeWithText("Home").assertDoesNotExist()
        composeTestRule.onNodeWithText("Merit badges").assertIsDisplayed()
    }

    @Test
    fun back_fromBadges_returnsHome() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Merit badges").performClick()
        // pressBack() doesn't wait for Compose, so let the Badges entry settle first;
        // otherwise back arrives before Navigation 3 handles it.
        composeTestRule.waitForIdle()

        pressBack()

        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
    }
}
