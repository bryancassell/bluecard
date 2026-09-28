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
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Local UI test: Robolectric launches the activity on the JVM with Hilt's test
 * application, so test modules (such as TestDispatchersModule) replace real ones. Each
 * test starts with no saved profile, as on a fresh install.
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    // Hilt must set up its components before the activity launches.
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    // An empty rule, so tests can save a profile before launching the activity.
    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var profileRepository: ProfileRepository

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun launchWithProfile() {
        runBlocking { profileRepository.saveProfile(Profile("Alex Scout", "123")) }
        launch()
    }

    private fun field(label: String) = composeTestRule.onNode(hasSetTextAction() and hasText(label))

    @Test
    fun firstLaunch_showsOnboarding() {
        launch()

        composeTestRule.onNodeWithText("Welcome to BlueCard").assertIsDisplayed()
        composeTestRule.onNodeWithText("Home").assertDoesNotExist()
    }

    @Test
    fun completingOnboarding_savesProfileAndShowsHome() {
        launch()

        field("Name").performTextInput("Alex Scout")
        field("Unit number").performTextInput("123")
        composeTestRule.onNodeWithText("Get started").performClick()

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
        field("Name").performTextInput("Alex Scout")
        field("Unit number").performTextInput("123")
        composeTestRule.onNodeWithText("Get started").performClick()
        composeTestRule.waitForIdle()

        // Onboarding was replaced by Home, so there is nothing to go back to.
        pressBackUnconditionally()

        // Robolectric doesn't move a finishing activity on to DESTROYED by itself.
        var isFinishing = false
        scenario.onActivity { isFinishing = it.isFinishing }
        assertTrue(isFinishing)
    }

    @Test
    fun launchWithProfile_goesStraightToHome() {
        launchWithProfile()

        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        composeTestRule.onNodeWithText("Welcome to BlueCard").assertDoesNotExist()
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
