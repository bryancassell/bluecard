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
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.catalog.FakeCatalogRepository
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.profile.FakeProfileRepository
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.data.progress.FakeProgressRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.di.DataModule
import io.github.bryancassell.bluecard.di.ProfileModule
import java.time.LocalDate
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
 * this class swaps the repositories for fakes. Each test starts with no saved profile, as
 * on a fresh install, and a one-badge catalog.
 */
@HiltAndroidTest
@UninstallModules(ProfileModule::class, DataModule::class)
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

    @BindValue
    @JvmField
    val catalogRepository: CatalogRepository = FakeCatalogRepository(
        listOf(
            MeritBadge(
                id = "camping",
                name = "Camping",
                summary = "Our summary of Camping.",
                officialUrl = "https://www.scouting.org/merit-badges/camping/",
                eagleRequired = true,
                requirementVersions = listOf(
                    RequirementsVersion(
                        LocalDate.of(2026, 1, 1),
                        listOf(Requirement("1", "First."))
                    )
                )
            )
        )
    )

    @BindValue
    @JvmField
    val progressRepository: ProgressRepository = FakeProgressRepository()

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
        // Home's button also says "Merit badges", so check that Home is gone.
        composeTestRule.onNodeWithText("Home").assertDoesNotExist()
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
        composeTestRule.onNodeWithText("Camping").assertIsDisplayed()
    }

    @Test
    fun openBadges_showsProgress() {
        runBlocking {
            progressRepository.startBadge(
                "camping",
                requirementsVersion = LocalDate.of(2026, 1, 1),
                startedDate = LocalDate.of(2026, 3, 1)
            )
        }
        launchWithProfile()

        composeTestRule.onNodeWithText("Merit badges").performClick()

        composeTestRule.onNodeWithText("In progress").assertIsDisplayed()
    }

    @Test
    fun openBadge_showsBadgeDetail() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Merit badges").performClick()

        composeTestRule.onNodeWithText("Camping").performClick()

        composeTestRule.onNodeWithText("Badge detail").assertIsDisplayed()
        // The placeholder shows the ID of the badge it was opened for.
        composeTestRule.onNodeWithText("camping").assertIsDisplayed()
        composeTestRule.onNodeWithText("Camping").assertDoesNotExist()
    }

    @Test
    fun back_fromBadgeDetail_returnsToBadges() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Merit badges").performClick()
        composeTestRule.onNodeWithText("Camping").performClick()
        // As in back_fromBadges_returnsHome, let the new entry settle before pressing back.
        composeTestRule.waitForIdle()

        pressBack()

        composeTestRule.onNodeWithText("Camping").assertIsDisplayed()
        composeTestRule.onNodeWithText("Badge detail").assertDoesNotExist()
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

    @Test
    fun doubleTap_onBadge_opensItOnce() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Merit badges").performClick()

        // Let the list finish animating in; until then it doesn't take taps either.
        val camping = composeTestRule.onNodeWithText("Camping").assertIsDisplayed()

        // Tap a second time while the list is still fading out, as a quick double tap
        // does. The list is still on screen then, and still takes taps that the incoming
        // screen doesn't. The second tap fails the test if the list is already gone.
        composeTestRule.mainClock.autoAdvance = false
        camping.performClick()
        composeTestRule.mainClock.advanceTimeBy(100)
        camping.performClick()
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        pressBack()

        composeTestRule.onNodeWithText("Camping").assertIsDisplayed()
    }
}
