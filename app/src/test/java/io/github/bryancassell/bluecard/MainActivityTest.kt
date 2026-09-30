package io.github.bryancassell.bluecard

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.core.os.bundleOf
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
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
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition
import io.github.bryancassell.bluecard.data.profile.FakeProfileRepository
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.data.progress.BadgeStart
import io.github.bryancassell.bluecard.data.progress.Counselor
import io.github.bryancassell.bluecard.data.progress.FakeProgressRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import io.github.bryancassell.bluecard.di.ClockModule
import io.github.bryancassell.bluecard.di.DataModule
import io.github.bryancassell.bluecard.di.ProfileModule
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * Local UI test: Robolectric launches the activity on the JVM with Hilt's test
 * application, so test modules (such as TestDispatchersModule) replace real ones, and
 * this class swaps the repositories for fakes and fixes the date. Each test starts with no
 * saved profile, as on a fresh install, a one-badge catalog and no progress.
 */
@HiltAndroidTest
@UninstallModules(ProfileModule::class, DataModule::class, ClockModule::class)
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
                        listOf(
                            Requirement(
                                "1",
                                "First.",
                                tracker = TrackerDefinition(
                                    listOf(
                                        TrackerColumn("night", "Night", TrackerColumnType.DATE),
                                        TrackerColumn("notes", "Notes", TrackerColumnType.TEXT)
                                    ),
                                    "night",
                                    "nights"
                                )
                            ),
                            Requirement(
                                "2",
                                "Second.",
                                requiredCount = 1,
                                children = listOf(
                                    Requirement("2a", "Choice A."),
                                    Requirement(
                                        "2b",
                                        "Choice B.",
                                        children = listOf(Requirement("2b(1)", "Part of B."))
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
    )

    @BindValue
    @JvmField
    val progressRepository: ProgressRepository = FakeProgressRepository()

    private val today = LocalDate.of(2026, 5, 20)

    @BindValue
    @JvmField
    val clock: Clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

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

    // MainActivity is exported, so another app can start it with any extras. These are named
    // like the screens' saved text fields, and built like the text they keep.
    private fun launchWithExtrasNamedLikeTextFields() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        for (key in listOf("name", "unit_number", "query", "comment", "phone", "email")) {
            intent.putExtra(key, bundleOf("text" to "From another app."))
        }
        scenario = ActivityScenario.launch(intent)
    }

    // Home's heading is the scout's name. Matching the heading leaves out the Onboarding
    // field that holds the same name.
    private fun home() = composeTestRule.onNode(isHeading() and hasText("Alex Scout"))

    // Without a profile, Home would show only its loading indicator.
    private fun homeLoading() = composeTestRule.onNode(
        SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo.Indeterminate
        )
    )

    private fun field(label: String) = composeTestRule.onNode(hasSetTextAction() and hasText(label))

    private fun assertFieldEmpty(label: String) {
        field(label).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))
        )
    }

    private fun completeOnboarding() {
        field("Name").performTextInput("Alex Scout")
        field("Unit number").performTextInput("123")
        composeTestRule.onNodeWithText("Get started").performClick()
    }

    /** Taps twice before the next frame, before the first tap's screen change starts. */
    private fun tapTwiceInOneFrame(text: String) {
        // Let the screen finish appearing before stopping the clock.
        val node = composeTestRule.onNodeWithText(text).assertIsDisplayed()
        composeTestRule.mainClock.autoAdvance = false
        node.performClick()
        node.performClick()
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
    }

    private fun openCamping() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Merit badges").performClick()
        composeTestRule.onNodeWithText("Camping").performClick()
    }

    // The checkbox on a requirement's own page.
    private fun completedCheckbox() =
        composeTestRule.onNode(hasText("Completed") and isToggleable())

    // Opens the requirement with this summary, marks it complete on its page, and goes back.
    private fun completeOnItsPage(summary: String) {
        composeTestRule.onNodeWithText(summary).performClick()
        completedCheckbox().performClick()
        composeTestRule.waitForIdle()
        pressBack()
    }

    private suspend fun recorded(number: String) = progressRepository.observeProgress("camping")
        .first()?.requirements?.singleOrNull { it.requirementNumber == number }

    private suspend fun trackerValues(number: String) = progressRepository
        .observeProgress("camping").first()?.trackerEntries.orEmpty()
        .filter { it.requirementNumber == number }.map { it.values }

    /** Badges is showing, and Badge detail isn't. */
    private fun assertBadgesShowing() {
        composeTestRule.onNodeWithText("Merit badges").assertIsDisplayed()
        composeTestRule.onNodeWithText("Our summary of Camping.").assertDoesNotExist()
    }

    /** Checks that Robolectric set up the device right-to-left, as "fa" asks. */
    private fun assertDeviceIsRightToLeft() {
        val device = ApplicationProvider.getApplicationContext<Application>().resources
        assertEquals(View.LAYOUT_DIRECTION_RTL, device.configuration.layoutDirection)
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
        home().assertDoesNotExist()
        homeLoading().assertDoesNotExist()
    }

    @Test
    fun unreadableProfile_showsLoadFailedMessage() {
        fakeProfileRepository.failLoads = true
        launch()

        composeTestRule.onNodeWithText(
            "Couldn't load your data. Try closing and reopening BlueCard."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Welcome to BlueCard").assertDoesNotExist()
        home().assertDoesNotExist()
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

        home().assertIsDisplayed()
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
        home().assertIsDisplayed()

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

        home().assertIsDisplayed()
        composeTestRule.onNodeWithText("Welcome to BlueCard").assertDoesNotExist()
    }

    @Test
    fun launchWithProfile_goesStraightToHome() {
        launchWithProfile()

        home().assertIsDisplayed()
        composeTestRule.onNodeWithText("Welcome to BlueCard").assertDoesNotExist()
    }

    @Test
    fun profileRemoved_showsOnboardingAgain() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Merit badges").performClick()
        // Home's button also says "Merit badges", so check that Home is gone.
        home().assertDoesNotExist()
        composeTestRule.onNodeWithText("Merit badges").assertIsDisplayed()

        fakeProfileRepository.removeProfile()

        composeTestRule.onNodeWithText("Welcome to BlueCard").assertIsDisplayed()
        composeTestRule.onNodeWithText("Merit badges").assertDoesNotExist()
    }

    @Test
    fun launchExtras_doNotFillOnboardingFields() {
        launchWithExtrasNamedLikeTextFields()

        assertFieldEmpty("Name")
        assertFieldEmpty("Unit number")
    }

    @Test
    fun launchExtras_doNotFillSearchOrComment() {
        runBlocking { profileRepository.saveProfile(Profile("Alex Scout", "123")) }
        launchWithExtrasNamedLikeTextFields()

        composeTestRule.onNodeWithText("Merit badges").performClick()
        assertFieldEmpty("Search merit badges")
        composeTestRule.onNodeWithText("Camping").performClick()
        composeTestRule.onNodeWithText("First.").performClick()
        assertFieldEmpty("Comment")
    }

    @Test
    fun launchExtras_doNotFillCounselorFields() {
        runBlocking { profileRepository.saveProfile(Profile("Alex Scout", "123")) }
        launchWithExtrasNamedLikeTextFields()

        composeTestRule.onNodeWithText("Merit badges").performClick()
        composeTestRule.onNodeWithText("Camping").performClick()
        composeTestRule.onNodeWithText("Add counselor").performScrollTo().performClick()
        assertFieldEmpty("Name")
        assertFieldEmpty("Phone")
        assertFieldEmpty("Email")
    }

    // Covers every ViewModel, including those scoped to the activity, whatever keys the
    // screens save their text under.
    @Test
    fun launchExtras_areNotDefaultArguments() {
        launchWithExtrasNamedLikeTextFields()

        var defaultArgs: Bundle? = null
        scenario.onActivity { defaultArgs = it.defaultViewModelCreationExtras[DEFAULT_ARGS_KEY] }
        assertEquals(emptySet<String>(), defaultArgs?.keySet())
    }

    @Test
    fun openBadges_showsBadges() {
        launchWithProfile()

        composeTestRule.onNodeWithText("Merit badges").performClick()

        home().assertDoesNotExist()
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
        openCamping()

        composeTestRule.onNodeWithText("Our summary of Camping.").assertIsDisplayed()
        composeTestRule.onNodeWithText("First.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Merit badges").assertDoesNotExist()
    }

    // The app formats every string in the strings' language, so on a Persian device the
    // English strings keep English digits rather than "Do ۱ of ۲".
    @Config(qualifiers = "fa")
    @Test
    fun onDeviceWithOtherDigits_numbersUseStringsLanguageDigits() {
        openCamping()

        composeTestRule.onNodeWithText("Do 1 of 2").assertIsDisplayed()
    }

    // The activity takes the strings' language's direction, so on a right-to-left device its
    // views are left-to-right like the English strings. Compose draws in one of them, and
    // keyboard and D-pad focus moves by its direction. Its resources are left-to-right too, and
    // ProvideStringsLanguageResources keeps their direction, so resources with a
    // direction-specific version, such as drawable-ldrtl, match the layout.
    @Config(qualifiers = "fa")
    @Test
    fun onRightToLeftDevice_activityIsLeftToRight() {
        assertDeviceIsRightToLeft()

        launchWithProfile()

        scenario.onActivity {
            assertEquals(View.LAYOUT_DIRECTION_LTR, it.window.decorView.layoutDirection)
            assertEquals(View.LAYOUT_DIRECTION_LTR, it.resources.configuration.layoutDirection)
        }
    }

    // Screens are laid out left-to-right too: a requirement's number comes before its text.
    // Text takes the layout's direction, which puts a sentence's final period at its end, but
    // a test can't see where Text draws it (see paragraphDirection).
    @Config(qualifiers = "fa")
    @Test
    fun onRightToLeftDevice_laysOutInStringsLanguageDirection() {
        assertDeviceIsRightToLeft()

        openCamping()

        // ListItem merges its texts into one node, so find each in the unmerged tree.
        val number = composeTestRule.onNodeWithText("1", useUnmergedTree = true)
            .getBoundsInRoot()
        val text = composeTestRule.onNodeWithText("First.", useUnmergedTree = true)
            .getBoundsInRoot()
        assertTrue(number.right <= text.left)
    }

    @Test
    fun back_fromBadgeDetail_returnsToBadges() {
        openCamping()
        // As in back_fromBadges_returnsHome, let the new entry settle before pressing back.
        composeTestRule.waitForIdle()

        pressBack()

        assertBadgesShowing()
    }

    @Test
    fun officialLink_opensOfficialPageInBrowser() {
        openCamping()

        composeTestRule.onNodeWithText("Official requirements").performClick()

        var started: Intent? = null
        scenario.onActivity { started = shadowOf(it).nextStartedActivity }
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals("https://www.scouting.org/merit-badges/camping/", started?.dataString)
    }

    @Test
    fun officialLink_withNoAppForLinks_showsMessage() {
        openCamping()
        // Starting an activity nothing can handle now fails, as on a phone where parental
        // controls block the browser.
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).checkActivities(true)

        composeTestRule.onNodeWithText("Official requirements").performClick()

        assertEquals("No app on this phone can open the link.", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun openRequirement_showsRequirementDetail() {
        openCamping()

        composeTestRule.onNodeWithText("Second.").performClick()

        composeTestRule.onNodeWithText("Requirement 2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Choice A.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Our summary of Camping.").assertDoesNotExist()
    }

    @Test
    fun openSubRequirement_showsItsOwnPage() {
        openCamping()
        composeTestRule.onNodeWithText("Second.").performClick()

        composeTestRule.onNodeWithText("Choice B.").performClick()

        composeTestRule.onNodeWithText("Requirement 2b").assertIsDisplayed()
        composeTestRule.onNodeWithText("Part of B.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Requirement 2").assertDoesNotExist()
    }

    @Test
    fun back_fromRequirementDetail_returnsToBadgeDetail() {
        openCamping()
        composeTestRule.onNodeWithText("Second.").performClick()
        composeTestRule.waitForIdle()

        pressBack()

        composeTestRule.onNodeWithText("Our summary of Camping.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Requirement 2").assertDoesNotExist()
    }

    // The acceptance test of recording progress: completing enough sub-requirements completes
    // their requirement, and completing every requirement completes the badge. The scout marks
    // each one complete on its own page.
    @Test
    fun completingEnoughRequirements_completesTheBadge() {
        openCamping()
        completeOnItsPage("First.")
        composeTestRule.onNodeWithText("Second.").performClick()

        // Requirement 2 needs one of its two choices.
        completeOnItsPage("Choice A.")

        composeTestRule.onNode(hasText("Choice A.") and hasContentDescription("Completed"))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Completed").assertIsDisplayed()
        composeTestRule.waitForIdle()
        pressBack()
        composeTestRule.onNode(hasText("Second.") and hasContentDescription("Completed"))
            .assertIsDisplayed()
        composeTestRule.waitForIdle()
        pressBack()
        composeTestRule.onNode(hasText("Camping") and hasText("Completed")).assertIsDisplayed()
    }

    @Test
    fun requirementPage_recordsCompletionDateAndComment() {
        openCamping()
        composeTestRule.onNodeWithText("First.").performClick()

        completedCheckbox().performClick()
        composeTestRule.onNodeWithText("Completed on May 20, 2026").assertIsDisplayed()
        composeTestRule.onNode(hasSetTextAction() and hasText("Comment"))
            .performTextInput("Planned it with my patrol.")
        composeTestRule.onNodeWithText("Save comment").performScrollTo().performClick()

        assertEquals(
            RequirementProgress("camping", "1", true, today, "Planned it with my patrol."),
            runBlocking { recorded("1") }
        )
        // Back on the badge's page, the requirement's row shows its check.
        composeTestRule.waitForIdle()
        pressBack()
        composeTestRule.onNode(hasText("First.") and hasContentDescription("Completed"))
            .assertIsDisplayed()
    }

    private fun notesField() = composeTestRule.onNode(hasSetTextAction() and hasText("Notes"))

    // The acceptance test of trackers: a row the scout adds is listed on the requirement's
    // page, counted on the badge's, and saved.
    @Test
    fun trackerRow_isAddedAndCounted() {
        openCamping()
        composeTestRule.onNodeWithText("First.").performClick()

        composeTestRule.onNodeWithText("Add night").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Night 1").assertIsDisplayed()
        notesField().performTextInput("Rained all night.")
        composeTestRule.onNodeWithText("Save").performScrollTo().performClick()

        // Saving closes the row's page.
        composeTestRule.onNodeWithText("1 night").performScrollTo().assertIsDisplayed()
        composeTestRule.onNode(hasText("Night 1") and hasText("Rained all night."))
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(
            listOf(mapOf("notes" to "Rained all night.")),
            runBlocking { trackerValues("1") }
        )
        composeTestRule.waitForIdle()
        pressBack()
        composeTestRule.onNode(hasText("First.") and hasText("1 night")).assertIsDisplayed()
    }

    @Test
    fun trackerRow_isEditedAndDeleted() {
        runBlocking {
            progressRepository.addTrackerEntry(
                "camping",
                "1",
                null,
                mapOf("notes" to "Clear skies."),
                BadgeStart(LocalDate.of(2026, 1, 1), today)
            )
        }
        openCamping()
        composeTestRule.onNodeWithText("First.").performClick()

        composeTestRule.onNodeWithText("Night 1").performScrollTo().performClick()
        notesField().performTextInput(" Saw a meteor.")
        composeTestRule.onNodeWithText("Save").performScrollTo().performClick()
        composeTestRule.onNode(hasText("Night 1") and hasText("Clear skies. Saw a meteor."))
            .performScrollTo()
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Night 1").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Delete").performScrollTo().performClick()
        composeTestRule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()

        composeTestRule.onNodeWithText("0 nights").performScrollTo().assertIsDisplayed()
        assertEquals(emptyList<Map<String, String>>(), runBlocking { trackerValues("1") })
    }

    @Test
    fun screenReaderClick_onAddWhileTheRowsPageCloses_doesNothing() {
        openCamping()
        composeTestRule.onNodeWithText("First.").performClick()
        composeTestRule.onNodeWithText("Add night").performScrollTo().performClick()
        notesField().performTextInput("Rained all night.")
        composeTestRule.waitForIdle()

        // Saving closes the row's page, which fades out over the requirement's page and takes
        // touches meanwhile. A screen reader's click still reaches Add night, which would open
        // the closing page again, with the ViewModel it has closed.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithText("Save").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(100)
        notesField().assertExists()
        composeTestRule.onNodeWithText("Add night").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        notesField().assertDoesNotExist()
        composeTestRule.onNodeWithText("1 night").performScrollTo().assertIsDisplayed()
    }

    private suspend fun counselor() =
        progressRepository.observeProgress("camping").first()?.badge?.counselor

    @Test
    fun counselor_savedOnItsPage_showsOnBadgeDetail() {
        openCamping()

        composeTestRule.onNodeWithText("Add counselor").performScrollTo().performClick()
        field("Name").performTextInput("Pat Lee")
        field("Phone").performTextInput("555-0100")
        field("Email").performTextInput("pat@example.com")
        composeTestRule.onNodeWithText("Save").performScrollTo().performClick()

        assertEquals(
            Counselor("Pat Lee", "555-0100", "pat@example.com"),
            runBlocking {
                counselor()
            }
        )
        // Saving closes the page, back to the badge's.
        field("Name").assertDoesNotExist()
        composeTestRule.onNodeWithText("Pat Lee").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("555-0100").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("pat@example.com").performScrollTo().assertIsDisplayed()

        // Editing it starts from what's saved.
        composeTestRule.onNodeWithText("Edit counselor").performScrollTo().performClick()
        field("Phone").performTextClearance()
        composeTestRule.onNodeWithText("Save").performScrollTo().performClick()

        assertEquals(
            Counselor(name = "Pat Lee", email = "pat@example.com"),
            runBlocking { counselor() }
        )
        composeTestRule.onNodeWithText("555-0100").assertDoesNotExist()
    }

    @Test
    fun back_fromEditCounselor_discardsChanges() {
        openCamping()
        composeTestRule.onNodeWithText("Add counselor").performScrollTo().performClick()
        field("Name").performTextInput("Pat Lee")
        composeTestRule.waitForIdle()

        pressBack()

        composeTestRule.onNodeWithText("Add counselor").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Pat Lee").assertDoesNotExist()
        assertNull(runBlocking { progressRepository.observeProgress("camping").first() })
    }

    // Dates, like numbers, follow the strings' language, so on a Persian device the English
    // strings keep English month names and digits.
    @Config(qualifiers = "fa")
    @Test
    fun onDeviceInOtherLanguage_datesUseStringsLanguage() {
        openCamping()
        composeTestRule.onNodeWithText("First.").performClick()

        completedCheckbox().performClick()

        composeTestRule.onNodeWithText("Completed on May 20, 2026").assertIsDisplayed()
    }

    // The date picker shows its labels and dates in the device's language, so it's laid out in
    // that language's direction, unlike the English screen behind it: a Persian calendar reads
    // right-to-left. Its buttons show the direction: OK comes first, on the left.
    @Config(qualifiers = "fa")
    @Test
    fun onRightToLeftDevice_datePickerIsRightToLeft() {
        assertDeviceIsRightToLeft()
        openCamping()
        composeTestRule.onNodeWithText("First.").performClick()
        completedCheckbox().performClick()

        composeTestRule.onNodeWithText("Change date").performScrollTo().performClick()

        val ok = composeTestRule.onNodeWithText("OK").getBoundsInRoot()
        val cancel = composeTestRule.onNodeWithText("Cancel").getBoundsInRoot()
        assertTrue(ok.right <= cancel.left)
    }

    @Test
    fun back_fromBadges_returnsHome() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Merit badges").performClick()
        // pressBack() doesn't wait for Compose, so let the Badges entry settle first;
        // otherwise back arrives before Navigation 3 handles it.
        composeTestRule.waitForIdle()

        pressBack()

        home().assertIsDisplayed()
    }

    @Test
    fun home_showsProgressAsSoonAsItChanges() {
        launchWithProfile()
        composeTestRule.onNodeWithText("You haven't started any merit badges yet.")
            .assertIsDisplayed()

        // As when the scout starts a badge on another screen.
        runBlocking {
            progressRepository.startBadge(
                "camping",
                requirementsVersion = LocalDate.of(2026, 1, 1),
                startedDate = LocalDate.of(2026, 3, 1)
            )
        }

        composeTestRule.onNodeWithText("Your merit badges").assertIsDisplayed()
        composeTestRule.onNodeWithText("0 of 1 completed").assertIsDisplayed()
    }

    @Test
    fun openDataManagement_showsDataManagement() {
        launchWithProfile()

        composeTestRule.onNodeWithText("Manage data").performClick()

        home().assertDoesNotExist()
        composeTestRule.onNodeWithText("Data management").assertIsDisplayed()
    }

    @Test
    fun back_fromDataManagement_returnsHome() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Manage data").performClick()
        // As in back_fromBadges_returnsHome, let the new entry settle before pressing back.
        composeTestRule.waitForIdle()

        pressBack()

        home().assertIsDisplayed()
    }

    @Test
    fun doubleTap_onMeritBadges_opensBadgesOnce() {
        launchWithProfile()

        tapTwiceInOneFrame("Merit badges")
        pressBack()

        home().assertIsDisplayed()
    }

    @Test
    fun doubleTap_onManageData_opensDataManagementOnce() {
        launchWithProfile()

        tapTwiceInOneFrame("Manage data")
        pressBack()

        home().assertIsDisplayed()
    }

    @Test
    fun doubleTap_onBadge_opensItOnce() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Merit badges").performClick()

        // Let the list finish animating in. Badge detail's heading also says "Camping", so
        // match the list's row.
        val camping = composeTestRule.onNode(hasText("Camping") and hasClickAction())
            .assertIsDisplayed()

        // Tap a second time while the list is still fading out, as a quick double tap
        // does. The second tap fails the test if the list is already gone. Screens ignore
        // touches while they animate, so neither screen takes it;
        // doubleTap_onRequirement_opensItOnce shows navigation guarding a second tap that
        // does reach the screen, in the same frame.
        composeTestRule.mainClock.autoAdvance = false
        camping.performClick()
        composeTestRule.mainClock.advanceTimeBy(100)
        camping.performClick()
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        pressBack()

        assertBadgesShowing()
    }

    @Test
    fun doubleTap_onBadge_doesNotPressOfficialLink() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Merit badges").performClick()
        val camping = composeTestRule.onNode(hasText("Camping") and hasClickAction())
            .assertIsDisplayed()

        // The second tap of a quick double tap lands on Badge detail, which is fading in on
        // top, 100 ms after the first. Which control is under the finger depends on the
        // layout, so tap the link itself, as a double tap on a badge over it would.
        composeTestRule.mainClock.autoAdvance = false
        camping.performClick()
        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.onNodeWithText("Official requirements").performClick()
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        var started: Intent? = null
        scenario.onActivity { started = shadowOf(it).nextStartedActivity }
        assertNull(started)
    }

    @Test
    fun tap_onClosingScreenAfterBack_doesNothing() {
        openCamping()
        composeTestRule.waitForIdle()

        // Badge detail is drawn on top of Badges while it fades out after Back. Tap its
        // link after the double-tap timeout, while it's still there.
        composeTestRule.mainClock.autoAdvance = false
        pressBack()
        // Under Robolectric, Espresso doesn't wait for Compose, and nothing else in this test
        // runs Compose's coroutines before the tap, so hand the back stack change to Compose
        // now. With the clock stopped, this doesn't let time pass.
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(400)
        // Badges is fading in under Badge detail.
        composeTestRule.onNodeWithText("Merit badges").assertExists()
        composeTestRule.onNodeWithText("Official requirements").performClick()
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        var started: Intent? = null
        scenario.onActivity { started = shadowOf(it).nextStartedActivity }
        assertNull(started)
        assertBadgesShowing()
    }

    @Test
    fun doubleTap_onRequirement_opensItOnce() {
        openCamping()

        tapTwiceInOneFrame("Second.")
        pressBack()

        composeTestRule.onNodeWithText("Our summary of Camping.").assertIsDisplayed()
    }

    @Test
    fun doubleTap_onSubRequirement_opensItOnce() {
        openCamping()
        composeTestRule.onNodeWithText("Second.").performClick()

        tapTwiceInOneFrame("Choice B.")
        pressBack()

        composeTestRule.onNodeWithText("Requirement 2").assertIsDisplayed()
    }

    @Test
    fun doubleTap_onAddCounselor_opensItOnce() {
        openCamping()
        composeTestRule.onNodeWithText("Add counselor").performScrollTo()

        tapTwiceInOneFrame("Add counselor")
        pressBack()

        composeTestRule.onNodeWithText("Our summary of Camping.").assertIsDisplayed()
    }

    @Test
    fun tap_onScreenStillAnimatingIn_opensIt() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Merit badges").assertIsDisplayed()

        // Tap a badge 400 ms after opening the list, after the 300 ms double-tap timeout,
        // while it is still fading in.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithText("Merit badges").performClick()
        composeTestRule.mainClock.advanceTimeBy(400)
        home().assertExists()
        composeTestRule.onNodeWithText("Camping").performClick()
        composeTestRule.mainClock.autoAdvance = true

        composeTestRule.onNodeWithText("Our summary of Camping.").assertIsDisplayed()
    }

    @Test
    fun tap_onBadgesLeavingForOnboarding_doesNothing() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Merit badges").performClick()
        val camping = composeTestRule.onNodeWithText("Camping").assertIsDisplayed()

        // The profile goes missing, and the scout taps a badge while Badges animates out.
        composeTestRule.mainClock.autoAdvance = false
        fakeProfileRepository.removeProfile()
        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.onNodeWithText("Welcome to BlueCard").assertExists()
        // Run the row's click action directly: whether a real tap reaches Badges depends
        // on what Onboarding has drawn over that spot.
        camping.performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.mainClock.autoAdvance = true
        completeOnboarding()

        // Back where the scout was, not on a Badge detail opened while Onboarding showed.
        assertBadgesShowing()
    }
}
