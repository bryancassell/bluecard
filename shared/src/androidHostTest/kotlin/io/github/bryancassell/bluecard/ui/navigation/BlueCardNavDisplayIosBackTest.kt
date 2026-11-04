package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.catalog.FakeCatalogRepository
import io.github.bryancassell.bluecard.data.profile.FakeProfileRepository
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.data.progress.DamagedProgressRepository
import io.github.bryancassell.bluecard.data.progress.FakeDamagedProgressRepository
import io.github.bryancassell.bluecard.data.progress.FakeProgressRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.report.FakeReportRepository
import io.github.bryancassell.bluecard.data.report.ReportRepository
import io.github.bryancassell.bluecard.di.ZonedClock
import io.github.bryancassell.bluecard.testing.AppKoinRule
import io.github.bryancassell.bluecard.testing.BackSwipes
import io.github.bryancassell.bluecard.testing.FakeClock
import io.github.bryancassell.bluecard.testing.setContentWithText
import io.github.bryancassell.bluecard.testing.testIoDispatcherModule
import io.github.bryancassell.bluecard.ui.theme.BlueCardTheme
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules

/**
 * Back on iOS, through the real navigation root and pages, fed by fakes: the Back button, and a
 * back swipe that moves the pages. iOS's edge swipe is reported as [BackSwipes] reports it.
 * MainActivityTest checks Back on Android.
 *
 * It doesn't run the accessibility checks (AccessibilityChecks), whose native graphics would
 * change where text is laid out but not what's checked here. The screens' own tests check what
 * they show.
 */
@RunWith(AndroidJUnit4::class)
class BlueCardNavDisplayIosBackTest {
    @get:Rule(order = 0)
    val koinRule = AppKoinRule()

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    private val swipes = BackSwipes(composeTestRule)

    private val profileRepository = FakeProfileRepository()
    private val catalogRepository = FakeCatalogRepository()
    private val progressRepository = FakeProgressRepository()

    @Before
    fun replaceDependencies() {
        runBlocking { profileRepository.saveProfile(Profile("Alex Scout", "123")) }
        loadKoinModules(testIoDispatcherModule)
        // Declared one by one: a module of them declared here fails the Koin compiler plugin's
        // graph check, which then can't see the navigation keys the ViewModels are given.
        with(koinRule.koin) {
            declare<ProfileRepository>(profileRepository)
            declare<CatalogRepository>(catalogRepository)
            declare<ProgressRepository>(progressRepository)
            declare<DamagedProgressRepository>(FakeDamagedProgressRepository())
            declare<ReportRepository>(FakeReportRepository(progressRepository, catalogRepository))
            declare<ZonedClock>(FakeClock(Instant.parse("2026-05-20T12:00:00Z")))
        }
    }

    private fun launch(iosBack: Boolean = true, isSetUp: Boolean = true) {
        composeTestRule.setContentWithText {
            BlueCardTheme {
                swipes.Connect()
                BlueCardNavDisplay(isSetUp = isSetUp, iosBack = iosBack)
            }
        }
    }

    private fun home() = composeTestRule.onNode(isHeading() and hasText("Alex Scout"))

    private fun dataManagement() =
        composeTestRule.onNode(isHeading() and hasText("Data management"))

    private fun editProfile() = composeTestRule.onNode(isHeading() and hasText("Name and unit"))

    // Edit profile's, whose text the tests don't change. Data management, beside it during a swipe,
    // has a "Name and unit" heading too, but no text fields.
    private fun unitNumberField() = composeTestRule.onNode(hasSetTextAction() and hasText("123"))

    private fun backButton() = composeTestRule.onNodeWithContentDescription("Back")

    private fun openDataManagement() {
        composeTestRule.onNodeWithText("Manage data").performClick()
        dataManagement().assertIsDisplayed()
    }

    private fun openEditProfile() {
        openDataManagement()
        composeTestRule.onNode(hasContentDescription("Edit name and unit") and hasClickAction())
            .performClick()
        unitNumberField().assertIsDisplayed()
    }

    /** Opens Edit profile from Home and changes the name, without saving it. */
    private fun changeTheName() {
        openEditProfile()
        composeTestRule.onNode(hasSetTextAction() and hasText("Alex Scout"))
            .performTextReplacement("Sam Scout")
    }

    private fun discardDialog() = composeTestRule.onNodeWithText("Discard changes?")

    // Unclipped, since a page slides partly off screen.
    private fun SemanticsNodeInteraction.left() = getUnclippedBoundsInRoot().left

    /** Freezes time with a swipe in progress, so the pages can be measured where it left them. */
    private fun startSwipeAndHold(progress: Float) {
        composeTestRule.mainClock.autoAdvance = false
        swipes.start(progress)
        // NavDisplay seeks the pages to the swipe a few frames later. Waiting for idle runs what
        // each frame queues, without moving time on.
        repeat(5) {
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.waitForIdle()
        }
    }

    private fun letTimeRun() {
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
    }

    @Test
    fun home_hasNoBackButton() {
        launch()

        home().assertIsDisplayed()
        backButton().assertDoesNotExist()
    }

    @Test
    fun onboarding_hasNoBackButton() {
        launch(isSetUp = false)

        composeTestRule.onNodeWithText("Get started").assertIsDisplayed()
        backButton().assertDoesNotExist()
    }

    @Test
    fun backButton_goesBack() {
        launch()
        openDataManagement()

        backButton().performClick()

        home().assertIsDisplayed()
        dataManagement().assertDoesNotExist()
        backButton().assertDoesNotExist()
    }

    @Test
    fun backButton_onAPageWithUnsavedChanges_asksFirst() {
        launch()
        changeTheName()

        backButton().performClick()

        discardDialog().assertIsDisplayed()
        editProfile().assertIsDisplayed()
    }

    @Test
    fun backButton_tappedTwiceInOneFrame_closesOnlyItsPage() {
        launch()
        openEditProfile()

        // The second tap lands on Edit profile's button before a frame shows Data management.
        composeTestRule.mainClock.autoAdvance = false
        backButton().performClick()
        backButton().performClick()
        letTimeRun()

        dataManagement().assertIsDisplayed()
        home().assertDoesNotExist()
    }

    @Test
    fun swipe_movesThePagesWithTheFinger() {
        launch()
        openDataManagement()
        val width = composeTestRule.onRoot().getBoundsInRoot().width
        val atRest = dataManagement().left()

        startSwipeAndHold(progress = 0.4f)

        // Data management has moved 40% of the width, with Home beside it.
        assertEquals((width * 0.4f).value, (dataManagement().left() - atRest).value, 1f)
        assertEquals((dataManagement().left() - width).value, home().left().value, 1f)
    }

    @Test
    fun swipe_held_leavesThePageUnderneathTakingNoInput() {
        launch()
        openDataManagement()
        startSwipeAndHold(progress = 0.4f)
        // Past the double-tap timeout, when a page that's opening starts taking input.
        composeTestRule.mainClock.advanceTimeBy(500)

        composeTestRule.onNodeWithText("Manage data").requestFocus()

        composeTestRule.onNodeWithText("Manage data").assertIsNotFocused()
    }

    @Test
    fun swipe_completed_goesBack() {
        launch()
        openDataManagement()
        startSwipeAndHold(progress = 0.4f)

        swipes.complete()
        letTimeRun()

        home().assertIsDisplayed()
        dataManagement().assertDoesNotExist()
    }

    @Test
    fun swipe_cancelled_leavesThePageWhereItWas() {
        launch()
        openDataManagement()
        val atRest = dataManagement().left()
        startSwipeAndHold(progress = 0.4f)

        swipes.cancel()
        letTimeRun()

        assertEquals(atRest, dataManagement().left())
        home().assertDoesNotExist()
    }

    @Test
    fun swipe_onAPageWithUnsavedChanges_leavesThePageStill_andAsksOnceCompleted() {
        launch()
        changeTheName()
        val atRest = unitNumberField().left()

        startSwipeAndHold(progress = 0.4f)
        assertEquals(atRest, unitNumberField().left())
        swipes.complete()
        letTimeRun()

        discardDialog().assertIsDisplayed()
        assertEquals(atRest, unitNumberField().left())
    }

    @Test
    fun swipe_onceTheChangesAreUndone_movesThePage() {
        launch()
        changeTheName()
        val atRest = unitNumberField().left()

        composeTestRule.onNode(hasSetTextAction() and hasText("Sam Scout"))
            .performTextReplacement("Alex Scout")
        // Save turns off once the page shows nothing changed.
        composeTestRule.onNodeWithText("Save").assertIsNotEnabled()
        startSwipeAndHold(progress = 0.4f)

        assertTrue(unitNumberField().left() > atRest + 16.dp)
    }

    @Test
    fun withoutIosBack_pagesHaveNoBackButton() {
        launch(iosBack = false)

        openDataManagement()

        backButton().assertDoesNotExist()
    }
}
