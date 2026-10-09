package io.github.bryancassell.bluecard

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.core.content.IntentCompat
import androidx.core.graphics.Insets
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.catalog.FakeCatalogRepository
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.MeritBadgesNeeded
import io.github.bryancassell.bluecard.data.catalog.Rank
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
import io.github.bryancassell.bluecard.data.progress.DamagedProgressRepository
import io.github.bryancassell.bluecard.data.progress.FakeDamagedProgressRepository
import io.github.bryancassell.bluecard.data.progress.FakeProgressRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import io.github.bryancassell.bluecard.data.report.FakeReportRepository
import io.github.bryancassell.bluecard.data.report.ReportRepository
import io.github.bryancassell.bluecard.di.ClockModule
import io.github.bryancassell.bluecard.di.DataModule
import io.github.bryancassell.bluecard.di.ProfileModule
import io.github.bryancassell.bluecard.di.ReportModule
import io.github.bryancassell.bluecard.testing.DATE_PICKER_SCREEN
import io.github.bryancassell.bluecard.testing.FakeClock
import io.github.bryancassell.bluecard.testing.NARROW_SCREEN
import io.github.bryancassell.bluecard.testing.hasLine
import io.github.bryancassell.bluecard.testing.onReadAsOne
import io.github.bryancassell.bluecard.testing.pickerDateField
import io.github.bryancassell.bluecard.testing.pickerDay
import io.github.bryancassell.bluecard.testing.waitPastDateFieldFocusDelay
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
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
@UninstallModules(
    ProfileModule::class,
    DataModule::class,
    ReportModule::class,
    ClockModule::class
)
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
                                        TrackerColumn("weather", "Weather", TrackerColumnType.TEXT)
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
        ),
        listOf("Scout", "Tenderfoot").map { name ->
            // Tenderfoot's 2 asks for a merit badge.
            val earn =
                Requirement("2", "Earn a merit badge.", meritBadges = MeritBadgesNeeded(1, 1))
            Rank(
                id = name.lowercase(),
                name = name,
                summary = "Our summary of $name.",
                officialUrl = "https://www.scouting.org/$name.pdf",
                requirementVersions = listOf(
                    RequirementsVersion(
                        LocalDate.of(2026, 1, 1),
                        listOf(Requirement("1", "$name's first.")) +
                            listOfNotNull(earn.takeIf { name == "Tenderfoot" })
                    )
                )
            )
        }
    )

    @BindValue
    @JvmField
    val progressRepository: ProgressRepository = FakeProgressRepository()

    private val fakeDamagedProgressRepository = FakeDamagedProgressRepository()

    @BindValue
    @JvmField
    val damagedProgressRepository: DamagedProgressRepository = fakeDamagedProgressRepository

    // PdfDocument only runs on a device.
    private val fakeReportRepository = FakeReportRepository(progressRepository, catalogRepository)

    @BindValue
    @JvmField
    val reportRepository: ReportRepository = fakeReportRepository

    private val today = LocalDate.of(2026, 5, 20)

    private val fakeClock = FakeClock(today.atTime(12, 0).toInstant(ZoneOffset.UTC))

    @BindValue
    @JvmField
    val clock: Clock = fakeClock

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

    /** Home's rank card, read with [part] in its label. */
    private fun rankCard(part: String) =
        composeTestRule.onNode(hasContentDescription(part, substring = true) and isHeading())

    // Home's Ranks button, rather than the Ranks screen's title.
    private fun ranksButton() = composeTestRule.onNode(hasText("Ranks") and hasClickAction())

    // Without a profile, Home would show only its loading indicator.
    private fun homeLoading() = composeTestRule.onNode(
        SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo.Indeterminate
        )
    )

    private fun field(label: String) = composeTestRule.onNode(hasSetTextAction() and hasText(label))

    private fun assertFieldText(label: String, text: String) {
        field(label).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text))
        )
    }

    private fun assertFieldEmpty(label: String) = assertFieldText(label, "")

    private fun completeOnboarding() {
        field("Name").performTextInput("Alex Scout")
        field("Unit number").performTextInput("123")
        composeTestRule.onNodeWithText("Get started").performClick()
    }

    /** Taps twice before the next frame, before the first tap's screen change starts. */
    private fun tapTwiceInOneFrame(text: String) =
        tapTwiceInOneFrame(composeTestRule.onNodeWithText(text))

    private fun tapTwiceInOneFrame(node: SemanticsNodeInteraction) {
        // Let the screen finish appearing before stopping the clock.
        node.assertIsDisplayed()
        composeTestRule.mainClock.autoAdvance = false
        node.performClick()
        node.performClick()
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
    }

    private fun openCamping() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Merit badges").performScrollTo().performClick()
        composeTestRule.onReadAsOne("Camping").performClick()
    }

    // The checkbox on a requirement's own page.
    private fun completedCheckbox() =
        composeTestRule.onNode(hasText("Completed") and isToggleable())

    // Opens the requirement with this summary, marks it complete on its page, and goes back.
    // Badge detail's status card can push the requirement down the page.
    private fun completeOnItsPage(summary: String) {
        composeTestRule.onReadAsOne(summary).performScrollTo().performClick()
        completedCheckbox().performClick()
        composeTestRule.waitForIdle()
        pressBack()
    }

    private suspend fun recorded(number: String) = progressRepository.observeProgress("camping")
        .first()?.requirements?.singleOrNull { it.requirementNumber == number }

    private suspend fun trackerValues(number: String) = progressRepository
        .observeProgress("camping").first()?.trackerEntries.orEmpty()
        .filter { it.requirementNumber == number }.map { it.values }

    /**
     * Home is showing again. It's found by Camping's row rather than the scout's name, because
     * Home keeps its scroll position, which can leave the name above the screen.
     */
    private fun assertHomeBackAtCamping() {
        home().assertExists()
        composeTestRule.onReadAsOne("Camping").assertIsDisplayed()
    }

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
    fun damagedProgress_showsNoticeOverHome() {
        fakeDamagedProgressRepository.setAside()

        launchWithProfile()

        composeTestRule.onNode(
            hasText("Your progress couldn't be read") and hasAnyAncestor(isDialog())
        )
            .assertIsDisplayed()
        home().assertExists()
    }

    @Test
    fun progressSetAsideWhileOpen_showsNotice() {
        launchWithProfile()
        home().assertIsDisplayed()

        // As when SQLite finds the database damaged while it opens.
        fakeDamagedProgressRepository.setAside()

        composeTestRule.onNodeWithText("Your progress couldn't be read").assertIsDisplayed()
    }

    @Test
    fun damagedProgressNotice_ok_closesItForGood() {
        fakeDamagedProgressRepository.setAside()
        launchWithProfile()

        composeTestRule.onNodeWithText("OK").performClick()

        composeTestRule.onNode(isDialog()).assertDoesNotExist()
        home().assertIsDisplayed()
        assertEquals(
            false,
            runBlocking { damagedProgressRepository.observeNoticePending().first() }
        )
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
        composeTestRule.onReadAsOne("Camping").performClick()
        composeTestRule.onReadAsOne("First.").performScrollTo().performClick()
        assertFieldEmpty("Notes")
    }

    @Test
    fun launchExtras_doNotFillCounselorFields() {
        runBlocking { profileRepository.saveProfile(Profile("Alex Scout", "123")) }
        launchWithExtrasNamedLikeTextFields()

        composeTestRule.onNodeWithText("Merit badges").performClick()
        composeTestRule.onReadAsOne("Camping").performClick()
        composeTestRule.onNodeWithText("Add counselor").performScrollTo().performClick()
        assertFieldEmpty("Name")
        assertFieldEmpty("Phone")
        assertFieldEmpty("Email")
    }

    @Test
    fun launchExtras_doNotFillNameAndUnitFields() {
        runBlocking { profileRepository.saveProfile(Profile("Alex Scout", "123")) }
        launchWithExtrasNamedLikeTextFields()

        composeTestRule.onNodeWithText("Manage data").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Edit").performClick()
        assertFieldText("Name", "Alex Scout")
        assertFieldText("Unit number", "123")
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
        composeTestRule.onReadAsOne("Camping").assertIsDisplayed()
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

        composeTestRule.onNodeWithText("Merit badges").performScrollTo().performClick()

        composeTestRule.onReadAsOne("In progress").assertIsDisplayed()
    }

    @Test
    fun openBadge_showsBadgeDetail() {
        openCamping()

        composeTestRule.onNodeWithText("Our summary of Camping.").assertIsDisplayed()
        // Under the status card, down the page.
        composeTestRule.onReadAsOne("First.").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Merit badges").assertDoesNotExist()
    }

    @Test
    fun openBadgeInProgress_fromHome_showsBadgeDetail() {
        runBlocking {
            progressRepository.startBadge(
                "camping",
                requirementsVersion = LocalDate.of(2026, 1, 1),
                startedDate = LocalDate.of(2026, 3, 1)
            )
        }
        launchWithProfile()

        composeTestRule.onReadAsOne("Camping").performScrollTo().performClick()

        home().assertDoesNotExist()
        composeTestRule.onNodeWithText("Our summary of Camping.").assertIsDisplayed()
        pressBack()
        assertHomeBackAtCamping()
    }

    // The system bars' icons follow dark mode, as BlueCardTheme does, so they show on its
    // background: dark on the light scheme, and light on the dark one.
    @Config(qualifiers = "notnight")
    @Test
    fun inLightMode_systemBarIconsAreDark() {
        launchWithProfile()

        scenario.onActivity {
            val insetsController = WindowCompat.getInsetsController(it.window, it.window.decorView)
            assertTrue(insetsController.isAppearanceLightStatusBars)
            assertTrue(insetsController.isAppearanceLightNavigationBars)
        }
    }

    @Config(qualifiers = "night")
    @Test
    fun inDarkMode_systemBarIconsAreLight() {
        launchWithProfile()

        scenario.onActivity {
            val insetsController = WindowCompat.getInsetsController(it.window, it.window.decorView)
            assertFalse(insetsController.isAppearanceLightStatusBars)
            assertFalse(insetsController.isAppearanceLightNavigationBars)
        }
    }

    // The app formats every string in the strings' language, so on a Persian device the
    // English strings keep English digits rather than "Do ۱ of ۲".
    @Config(qualifiers = "fa")
    @Test
    fun onDeviceWithOtherDigits_numbersUseStringsLanguageDigits() {
        openCamping()

        composeTestRule.onReadAsOne("Do 1 of 2").performScrollTo().assertIsDisplayed()
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

    private fun openCampingCompleted() {
        runBlocking {
            progressRepository.setCompletedOnPriorDate(
                "camping",
                LocalDate.of(2025, 8, 1),
                BadgeStart(LocalDate.of(2026, 1, 1), today)
            )
        }
        openCamping()
    }

    @Test
    fun shareReport_opensShareSheetWithTheBadgesReport() {
        openCampingCompleted()

        composeTestRule.onNodeWithText("Share report").performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("camping"), fakeReportRepository.shared)
        var started: Intent? = null
        scenario.onActivity { started = shadowOf(it).nextStartedActivity }
        assertEquals(Intent.ACTION_CHOOSER, started?.action)
        val send =
            IntentCompat.getParcelableExtra(started!!, Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(
            FakeReportRepository.reportUri("camping"),
            IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java)
        )
    }

    @Test
    fun saveReport_savesTheBadgesReportWhereTheScoutChose() {
        openCampingCompleted()

        composeTestRule.onNodeWithText("Save report").performClick()
        val destination = Uri.parse("content://documents/camping-report.pdf")
        scenario.onActivity {
            val picker = shadowOf(it).nextStartedActivityForResult
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, picker.intent.action)
            val result = Intent().setData(destination)
            shadowOf(it).receiveResult(picker.intent, Activity.RESULT_OK, result)
        }
        composeTestRule.waitForIdle()

        assertEquals(listOf("camping" to destination), fakeReportRepository.saved)
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

        composeTestRule.onReadAsOne("Second.").performScrollTo().performClick()

        composeTestRule.onNodeWithText("Requirement 2").assertIsDisplayed()
        composeTestRule.onReadAsOne("Choice A.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Our summary of Camping.").assertDoesNotExist()
    }

    @Test
    fun openSubRequirement_showsItsOwnPage() {
        openCamping()
        composeTestRule.onReadAsOne("Second.").performScrollTo().performClick()

        composeTestRule.onReadAsOne("Choice B.").performClick()

        composeTestRule.onNodeWithText("Requirement 2b").assertIsDisplayed()
        composeTestRule.onReadAsOne("Part of B.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Requirement 2").assertDoesNotExist()
    }

    @Test
    fun back_fromRequirementDetail_returnsToBadgeDetail() {
        openCamping()
        composeTestRule.onReadAsOne("Second.").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        pressBack()

        // Back on Badge detail, where it was left: scrolled down to the requirement.
        composeTestRule.onReadAsOne("Second.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Our summary of Camping.").assertExists()
        composeTestRule.onNodeWithText("Requirement 2").assertDoesNotExist()
    }

    // The acceptance test of recording progress: completing enough sub-requirements completes
    // their requirement, and completing every requirement completes the badge. The scout marks
    // each one complete on its own page.
    @Test
    fun completingEnoughRequirements_completesTheBadge() {
        openCamping()
        completeOnItsPage("First.")
        // The badge in progress's bar pushes its requirements down the page.
        composeTestRule.onReadAsOne("Second.").performScrollTo().performClick()

        // Requirement 2 needs one of its two choices.
        completeOnItsPage("Choice A.")

        composeTestRule.onReadAsOne("Choice A.").assert(hasStateDescription("Completed"))
            .assertIsDisplayed()
        composeTestRule.onReadAsOne("Choice B.").assert(hasStateDescription("Not needed"))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Completed").assertIsDisplayed()
        composeTestRule.waitForIdle()
        pressBack()
        // The completed badge's report buttons push its requirements down the page.
        composeTestRule.onReadAsOne("Second.").assert(hasStateDescription("Completed"))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.waitForIdle()
        pressBack()
        composeTestRule.onReadAsOne("Camping").assert(hasLine("Completed")).assertIsDisplayed()
    }

    @Test
    fun requirementPage_recordsCompletionDateAndComment() {
        openCamping()
        composeTestRule.onReadAsOne("First.").performScrollTo().performClick()

        completedCheckbox().performClick()
        composeTestRule.onNodeWithText("Completed on May 20, 2026").assertIsDisplayed()
        composeTestRule.onNode(hasSetTextAction() and hasText("Notes"))
            .performTextInput("Planned it with my patrol.")
        composeTestRule.onNodeWithText("Save notes").performScrollTo().performClick()

        assertEquals(
            RequirementProgress("camping", "1", true, today, "Planned it with my patrol."),
            runBlocking { recorded("1") }
        )
        // Back on the badge's page, the requirement's row shows it's complete.
        composeTestRule.waitForIdle()
        pressBack()
        composeTestRule.onReadAsOne("First.").assert(hasStateDescription("Completed"))
            .assertIsDisplayed()
    }

    private fun weatherField() = composeTestRule.onNode(hasSetTextAction() and hasText("Weather"))

    // Launches the app, opens a new night on Camping 1's tracker and types in it, without saving.
    private fun typeAnUnsavedNight() {
        openCamping()
        composeTestRule.onReadAsOne("First.").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Add night").performScrollTo().performClick()
        weatherField().performTextInput("Rained all night.")
        composeTestRule.waitForIdle()
    }

    // The acceptance test of trackers: a row the scout adds is listed on the requirement's
    // page, counted on the badge's, and saved.
    @Test
    fun trackerRow_isAddedAndCounted() {
        openCamping()
        composeTestRule.onReadAsOne("First.").performScrollTo().performClick()

        composeTestRule.onNodeWithText("Add night").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Night 1").assertIsDisplayed()
        weatherField().performTextInput("Rained all night.")
        composeTestRule.onNodeWithText("Save").performScrollTo().performClick()

        // Saving closes the row's page.
        composeTestRule.onNodeWithText("1 night").performScrollTo().assertIsDisplayed()
        composeTestRule.onReadAsOne("Night 1").assert(hasLine("Rained all night."))
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(
            listOf(mapOf("weather" to "Rained all night.")),
            runBlocking { trackerValues("1") }
        )
        composeTestRule.waitForIdle()
        pressBack()
        composeTestRule.onReadAsOne("First.").assert(hasLine("1 night")).assertIsDisplayed()
    }

    @Test
    fun trackerRow_isEditedAndDeleted() {
        runBlocking {
            progressRepository.addTrackerEntry(
                "camping",
                "1",
                null,
                mapOf("weather" to "Clear skies."),
                today,
                BadgeStart(LocalDate.of(2026, 1, 1), today)
            )
        }
        openCamping()
        composeTestRule.onReadAsOne("First.").performScrollTo().performClick()

        composeTestRule.onReadAsOne("Night 1").performScrollTo().performClick()
        weatherField().performTextInput(" Saw a meteor.")
        composeTestRule.onNodeWithText("Save").performScrollTo().performClick()
        composeTestRule.onReadAsOne("Night 1").assert(hasLine("Clear skies. Saw a meteor."))
            .performScrollTo()
            .assertIsDisplayed()

        composeTestRule.onReadAsOne("Night 1").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Delete").performScrollTo().performClick()
        composeTestRule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()

        composeTestRule.onNodeWithText("0 nights").performScrollTo().assertIsDisplayed()
        assertEquals(emptyList<Map<String, String>>(), runBlocking { trackerValues("1") })
    }

    @Test
    fun screenReaderClick_onAddWhileTheRowsPageCloses_doesNothing() {
        typeAnUnsavedNight()

        // Saving closes the row's page, which slides away beside the requirement's page and
        // takes touches where it still is. A screen reader's click still reaches Add night,
        // which would open the closing page again, with the ViewModel it has closed.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithText("Save").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(100)
        weatherField().assertExists()
        composeTestRule.onNodeWithText("Add night").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        weatherField().assertDoesNotExist()
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

    private fun discardDialog() = composeTestRule.onNodeWithText("Discard changes?")

    @Test
    fun back_fromEditCounselor_withChanges_asksBeforeDiscardingThem() {
        openCamping()
        composeTestRule.onNodeWithText("Add counselor").performScrollTo().performClick()
        field("Name").performTextInput("Pat Lee")
        composeTestRule.waitForIdle()

        // Cancel keeps the page and the edit.
        pressBack()
        discardDialog().assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertFieldText("Name", "Pat Lee")

        pressBack()
        composeTestRule.onNodeWithText("Discard").performClick()

        composeTestRule.onNodeWithText("Add counselor").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Pat Lee").assertDoesNotExist()
        assertNull(runBlocking { progressRepository.observeProgress("camping").first() })
    }

    @Test
    fun back_fromTrackerEntry_withChanges_asksBeforeDiscardingThem() {
        typeAnUnsavedNight()

        pressBack()
        discardDialog().assertIsDisplayed()
        composeTestRule.onNodeWithText("Discard").performClick()

        composeTestRule.onNodeWithText("0 nights").performScrollTo().assertIsDisplayed()
        assertEquals(emptyList<Map<String, String>>(), runBlocking { trackerValues("1") })
    }

    @Test
    fun back_fromRequirementDetail_withUnsavedNotes_asksBeforeDiscardingThem() {
        openCamping()
        // Saved straight away, so Back doesn't ask about it.
        completeOnItsPage("First.")
        composeTestRule.onReadAsOne("First.").performScrollTo().performClick()
        field("Notes").performScrollTo().performTextInput("Planned it with my patrol.")
        composeTestRule.waitForIdle()

        pressBack()
        composeTestRule.onNodeWithText("Your changes to the notes haven't been saved.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Discard").performClick()

        // Back on Badge detail, where it was left: scrolled down to the requirement.
        composeTestRule.onReadAsOne("First.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Our summary of Camping.").assertExists()
        composeTestRule.onNodeWithText("Requirement 1").assertDoesNotExist()
        assertEquals(
            RequirementProgress("camping", "1", true, today, null),
            runBlocking { recorded("1") }
        )
    }

    @Test
    fun back_justAfterDiscarding_goesBackAgain_withoutAsking() {
        openCamping()
        composeTestRule.onNodeWithText("Add counselor").performScrollTo().performClick()
        field("Name").performTextInput("Pat Lee")
        composeTestRule.waitForIdle()
        pressBack()
        discardDialog().assertIsDisplayed()

        // Back again while the page slides away.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithText("Discard").performClick()
        // Hand the back stack change to Compose; see tap_onClosingScreenAfterBack_doesNothing.
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(100)
        field("Name").assertExists()
        pressBack()
        composeTestRule.mainClock.autoAdvance = true

        assertBadgesShowing()
        discardDialog().assertDoesNotExist()
    }

    @Test
    fun back_justAfterOpeningAPage_closesIt_withoutAskingAboutUnsavedNotes() {
        openCamping()
        composeTestRule.onReadAsOne("Second.").performScrollTo().performClick()
        field("Notes").performScrollTo().performTextInput("Chose B.")
        composeTestRule.onReadAsOne("Choice B.").performScrollTo()
        composeTestRule.waitForIdle()

        // Back before the next frame, while Requirement 2 is still the page drawn.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onReadAsOne("Choice B.").performClick()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.mainClock.autoAdvance = true

        composeTestRule.onNodeWithText("Requirement 2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Requirement 2b").assertDoesNotExist()
        discardDialog().assertDoesNotExist()
        assertFieldText("Notes", "Chose B.")
    }

    @Test
    fun twoBacksBeforeTheNextFrame_fromAPageOverUnsavedNotes_askAboutThem() {
        openCamping()
        composeTestRule.onReadAsOne("First.").performScrollTo().performClick()
        field("Notes").performScrollTo().performTextInput("Planned it with my patrol.")
        composeTestRule.onNodeWithText("Add night").performScrollTo().performClick()
        weatherField().assertIsDisplayed()

        // The second Back arrives before Requirement 1 is drawn again.
        scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
            it.onBackPressedDispatcher.onBackPressed()
        }

        composeTestRule.onNodeWithText("Your changes to the notes haven't been saved.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertFieldText("Notes", "Planned it with my patrol.")
    }

    // Dates, like numbers, follow the strings' language, so on a Persian device the English
    // strings keep English month names and digits.
    @Config(qualifiers = "fa")
    @Test
    fun onDeviceInOtherLanguage_datesUseStringsLanguage() {
        openCamping()
        composeTestRule.onReadAsOne("First.").performScrollTo().performClick()

        completedCheckbox().performClick()

        composeTestRule.onNodeWithText("Completed on May 20, 2026").assertIsDisplayed()
    }

    // The date picker shows its labels and dates in the device's language, so it's laid out in
    // that language's direction, unlike the English screen behind it: a Persian calendar reads
    // right-to-left. Its buttons show the direction: OK comes first, on the left.
    @Config(qualifiers = "fa-$DATE_PICKER_SCREEN")
    @Test
    fun onRightToLeftDevice_datePickerIsRightToLeft() {
        assertDeviceIsRightToLeft()
        openCamping()
        composeTestRule.onReadAsOne("First.").performScrollTo().performClick()
        completedCheckbox().performClick()

        composeTestRule.onNodeWithText("Change date").performScrollTo().performClick()

        val ok = composeTestRule.onNodeWithText("OK").getBoundsInRoot()
        val cancel = composeTestRule.onNodeWithText("Cancel").getBoundsInRoot()
        assertTrue(ok.right <= cancel.left)
    }

    // As when the system stops the app with the picker open, and the scout then crosses a date
    // line westward: the picker comes back with its selection, which it no longer offers.
    @Config(qualifiers = DATE_PICKER_SCREEN)
    @Test
    fun datePicker_restoredWithAnEarlierToday_cantConfirmADayItDoesntOffer() {
        openCamping()
        composeTestRule.onReadAsOne("First.").performScrollTo().performClick()
        completedCheckbox().performClick()
        composeTestRule.onNodeWithText("Change date").performScrollTo().performClick()
        // It opens at the completion date, today.
        composeTestRule.pickerDay("May 20, 2026")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        fakeClock.now -= Duration.ofDays(1)

        scenario.recreate()

        composeTestRule.pickerDay("May 20, 2026")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .assertIsNotEnabled()
        composeTestRule.onNodeWithText("OK").assertIsNotEnabled()
        composeTestRule.pickerDay("May 19, 2026").performClick()
        composeTestRule.onNodeWithText("OK").performClick()
        composeTestRule.waitForIdle()
        assertEquals(LocalDate.of(2026, 5, 19), runBlocking { recorded("1") }?.completedDate)
    }

    // The app is recreated when its window changes size, as when it's split or folded, and the
    // calendar doesn't fit a window narrower than a small phone's. The day picked comes back
    // typed. In touch mode, as on a phone without TalkBack or a keyboard, the field doesn't take
    // focus, which would open the keyboard.
    @Config(qualifiers = DATE_PICKER_SCREEN)
    @Test
    fun datePicker_restoredOnANarrowerWindow_switchesToTypingTheDate() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
        openCamping()
        composeTestRule.onReadAsOne("First.").performScrollTo().performClick()
        completedCheckbox().performClick()
        composeTestRule.onNodeWithText("Change date").performScrollTo().performClick()
        composeTestRule.pickerDay("May 12, 2026").performClick()

        RuntimeEnvironment.setQualifiers(NARROW_SCREEN)
        // Frame by frame, so a calendar drawn squeezed before the switch would show.
        composeTestRule.mainClock.autoAdvance = false
        scenario.recreate()
        composeTestRule.pickerDay("May 12, 2026").assertDoesNotExist()
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitPastDateFieldFocusDelay()

        composeTestRule.pickerDay("May 12, 2026").assertDoesNotExist()
        composeTestRule.pickerDateField()
            .assertIsDisplayed()
            .assertIsNotFocused()
        composeTestRule.onNodeWithText("OK").performClick()
        composeTestRule.waitForIdle()
        assertEquals(LocalDate.of(2026, 5, 12), runBlocking { recorded("1") }?.completedDate)
    }

    // As when the phone turns from a window too narrow for the calendar to one it fits. The
    // scout hasn't chosen to type, so the field doesn't take focus, which would open the keyboard,
    // until they switch to the calendar and back.
    @Config(qualifiers = NARROW_SCREEN)
    @Test
    fun datePicker_restoredTypingOnAWiderWindow_keepsTheKeyboardDownUntilTheScoutSwitches() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
        openCamping()
        composeTestRule.onReadAsOne("First.").performScrollTo().performClick()
        completedCheckbox().performClick()
        composeTestRule.onNodeWithText("Change date").performScrollTo().performClick()
        composeTestRule.pickerDateField()
            .assertIsDisplayed()

        RuntimeEnvironment.setQualifiers(DATE_PICKER_SCREEN)
        scenario.recreate()
        composeTestRule.waitPastDateFieldFocusDelay()

        composeTestRule.onNodeWithContentDescription("Switch to calendar input mode")
            .assertIsDisplayed()
        composeTestRule.pickerDateField()
            .assertIsNotFocused()

        composeTestRule.onNodeWithContentDescription("Switch to calendar input mode").performClick()
        composeTestRule.onNodeWithContentDescription("Switch to text input mode").performClick()
        composeTestRule.waitPastDateFieldFocusDelay()

        composeTestRule.pickerDateField()
            .assertIsFocused()
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

    private fun searchField() = field("Search merit badges")

    private fun assertNothingFocused() =
        composeTestRule.onAllNodes(isFocused()).assertCountEquals(0)

    private fun press(key: Key) = composeTestRule.onRoot().performKeyInput { pressKey(key) }

    /**
     * Scrolls [node] into view on its page, gives it input focus as Tab would, and presses Enter,
     * as a hardware keyboard does.
     */
    private fun pressWithEnter(node: SemanticsNodeInteraction) {
        node.performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()

        press(Key.Enter)
    }

    /**
     * Out of touch mode, gives Badges' search field focus and goes Back to Home. A phone is out
     * of touch mode after a key press, and stays so while TalkBack is on, since its gestures
     * don't touch the app. Robolectric starts out of touch mode anyway; this says so for the tests.
     */
    private fun leaveBadgesWithSearchFocused() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        launchWithProfile()
        composeTestRule.onNodeWithText("Merit badges").performClick()
        searchField().performClick().assertIsFocused()

        pressBack()

        home().assertIsDisplayed()
    }

    private fun reopenBadgesAfterSearchHadFocus() {
        leaveBadgesWithSearchFocused()
        composeTestRule.onNodeWithText("Merit badges").performClick()
    }

    @Test
    fun back_fromBadgesWithSearchFocused_outOfTouchMode_focusesNothingOnHome() {
        leaveBadgesWithSearchFocused()

        assertNothingFocused()
    }

    @Test
    fun reopenBadges_afterSearchHadFocus_outOfTouchMode_focusesNothing() {
        leaveBadgesWithSearchFocused()

        composeTestRule.onNodeWithText("Merit badges").performClick()

        assertNothingFocused()
    }

    // A Home item that took focus here would lose it as Home went, to the focus target around the
    // pages.
    @Test
    fun tab_whilePagesSlide_movesFocusIntoTheArrivingPage() {
        leaveBadgesWithSearchFocused()
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithText("Merit badges").performClick()
        // Partway through the slide, with Home still drawn.
        composeTestRule.mainClock.advanceTimeBy(100)
        home().assertExists()

        press(Key.Tab)

        searchField().assertIsFocused()
    }

    // Each arrow key goes to the page's first item, as on a phone where nothing is focused.
    // Robolectric's keys go straight to Compose, so a test there can't show the phone's way.
    @Test
    fun downKey_afterAFocusedPageLeft_movesFocusIntoThePageShown() {
        reopenBadgesAfterSearchHadFocus()

        press(Key.DirectionDown)

        searchField().assertIsFocused()
    }

    @Test
    fun upKey_afterAFocusedPageLeft_movesFocusIntoThePageShown() {
        reopenBadgesAfterSearchHadFocus()

        press(Key.DirectionUp)

        searchField().assertIsFocused()
    }

    @Test
    fun leftKey_afterAFocusedPageLeft_movesFocusIntoThePageShown() {
        reopenBadgesAfterSearchHadFocus()

        press(Key.DirectionLeft)

        searchField().assertIsFocused()
    }

    @Test
    fun rightKey_afterAFocusedPageLeft_movesFocusIntoThePageShown() {
        reopenBadgesAfterSearchHadFocus()

        press(Key.DirectionRight)

        searchField().assertIsFocused()
    }

    @Test
    fun enter_afterAFocusedPageLeft_movesFocusIntoThePageShown() {
        reopenBadgesAfterSearchHadFocus()

        press(Key.Enter)

        searchField().assertIsFocused()
    }

    @Test
    fun tab_afterAFocusedPageLeft_movesFocusIntoThePageShown() {
        reopenBadgesAfterSearchHadFocus()

        press(Key.Tab)

        searchField().assertIsFocused()
    }

    @Test
    fun shiftTab_afterAFocusedPageLeft_focusesThePagesLastItem() {
        reopenBadgesAfterSearchHadFocus()

        composeTestRule.onRoot().performKeyInput {
            withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) }
        }

        // Camping, the catalog's only badge.
        composeTestRule.onReadAsOne("Camping").assertIsFocused()
    }

    @Test
    fun back_afterFocusMovesIntoThePageShown_leavesIt() {
        reopenBadgesAfterSearchHadFocus()
        press(Key.Tab)
        searchField().assertIsFocused()

        pressBack()

        home().assertIsDisplayed()
    }

    @Test
    fun tab_whenNothingHasHadFocus_focusesThePagesFirstItem() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Merit badges").performClick()

        press(Key.Tab)

        searchField().assertIsFocused()
    }

    @Test
    fun tab_fromThePagesLastItem_wrapsAroundToItsFirst() {
        reopenBadgesAfterSearchHadFocus()
        press(Key.Tab)
        // Camping, the catalog's only badge, is the last item.
        press(Key.Tab)

        press(Key.Tab)

        searchField().assertIsFocused()
    }

    private fun notesField() = field("Notes")

    private fun saveNotesButton() = composeTestRule.onNodeWithText("Save notes")

    // Out of touch mode, opens Camping's page.
    private fun openCampingOutOfTouchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        openCamping()
    }

    // Out of touch mode, opens Camping's first requirement.
    private fun openRequirementOutOfTouchMode() {
        openCampingOutOfTouchMode()
        composeTestRule.onReadAsOne("First.").performScrollTo().performClick()
    }

    // Out of touch mode, types in a requirement's notes, ready to save them.
    private fun typeNotesOutOfTouchMode() {
        openRequirementOutOfTouchMode()
        notesField().performClick().performTextInput("Planned it with my patrol.")
        saveNotesButton().performScrollTo()
    }

    /**
     * Out of touch mode, types in a requirement's notes and saves them. Save closes the keyboard
     * by clearing focus, and the requirement's page stays open.
     */
    private fun saveNotesOutOfTouchMode() {
        typeNotesOutOfTouchMode()

        saveNotesButton().performClick()
    }

    // Otherwise, Android's View.clearFocus() asks the view to take focus again, and the page's
    // first item, the Completed checkbox, took it.
    @Test
    fun saveNotes_outOfTouchMode_focusesNothing() {
        saveNotesOutOfTouchMode()

        assertNothingFocused()
    }

    // The way a hardware keyboard saves, with Save focused.
    @Test
    fun enterOnSaveNotes_outOfTouchMode_focusesNothing() {
        typeNotesOutOfTouchMode()

        pressWithEnter(saveNotesButton())

        assertNothingFocused()
    }

    // TalkBack, Switch Access and Voice Access click Save without moving input focus. A page
    // opened from the notes field and closed again leaves focus with the focus target around the
    // pages, and the notes still to save.
    @Test
    fun accessibilityClickOnSave_afterReturningToThePage_focusesNothing() {
        typeNotesOutOfTouchMode()
        composeTestRule.onNodeWithText("Add night").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        // pressBack() doesn't wait for Compose, so let the new page settle first.
        composeTestRule.waitForIdle()
        pressBack()
        saveNotesButton().assertIsEnabled()

        saveNotesButton().performScrollTo().performSemanticsAction(SemanticsActions.OnClick)

        assertNothingFocused()
    }

    @Test
    fun tab_afterSavingNotes_movesFocusIntoThePage() {
        saveNotesOutOfTouchMode()

        press(Key.Tab)

        completedCheckbox().assertIsFocused()
    }

    // Focus is now on the focus target around the pages, which Back mustn't stop at.
    @Test
    fun back_afterSavingNotes_leavesThePage() {
        saveNotesOutOfTouchMode()
        // pressBack() doesn't wait for Compose, so let the saved notes settle first.
        composeTestRule.waitForIdle()

        pressBack()

        notesField().assertDoesNotExist()
        composeTestRule.onReadAsOne("First.").assertExists()
    }

    /**
     * Out of touch mode, presses Save on a page that closes once saved, and checks nothing on
     * it takes focus as it goes: its first item did, and a field that takes focus opens the
     * keyboard. [fieldOnThePage] shows the page is still there.
     */
    private fun saveOutOfTouchModeAndClose(fieldOnThePage: String) {
        composeTestRule.onNodeWithText("Save").performScrollTo()
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.onNodeWithText("Save").performClick()

        // Before the page closes.
        field(fieldOnThePage).assertExists()
        assertNothingFocused()
        composeTestRule.mainClock.autoAdvance = true
        field(fieldOnThePage).assertDoesNotExist()
        assertNothingFocused()
    }

    @Test
    fun saveCounselor_outOfTouchMode_focusesNothingAsThePageCloses() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        openCamping()
        composeTestRule.onNodeWithText("Add counselor").performScrollTo().performClick()
        field("Email").performClick().performTextInput("pat@example.com")

        saveOutOfTouchModeAndClose(fieldOnThePage = "Name")
    }

    @Test
    fun saveNameAndUnit_outOfTouchMode_focusesNothingAsThePageCloses() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        openEditNameAndUnit()
        field("Unit number").performClick().performTextReplacement("Crew 7")

        saveOutOfTouchModeAndClose(fieldOnThePage = "Name")
    }

    @Test
    fun saveTrackerRow_outOfTouchMode_focusesNothingAsThePageCloses() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        typeAnUnsavedNight()

        saveOutOfTouchModeAndClose(fieldOnThePage = "Weather")
    }

    /**
     * Out of touch mode, presses Remove date on a requirement's page with a key. The button
     * leaves composition as it's pressed, and Add date takes Change date's place.
     */
    private fun removeDateWithEnter() {
        openRequirementOutOfTouchMode()
        completedCheckbox().performClick()

        pressWithEnter(composeTestRule.onNodeWithText("Remove date"))

        composeTestRule.onNodeWithText("Add date").assertExists()
    }

    // Otherwise, Android's View.clearFocus() asks the view to take focus again, and the page's
    // first item, the Completed checkbox, took it.
    @Test
    fun enterOnRemoveDate_outOfTouchMode_focusesNothing() {
        removeDateWithEnter()

        assertNothingFocused()
    }

    @Test
    fun tab_afterRemovingADate_movesFocusIntoThePage() {
        removeDateWithEnter()

        press(Key.Tab)

        completedCheckbox().assertIsFocused()
    }

    // Focus is now on the focus target around the pages, which Back mustn't stop at.
    @Test
    fun back_afterRemovingADate_leavesThePage() {
        removeDateWithEnter()
        // pressBack() doesn't wait for Compose, so let focus settle first.
        composeTestRule.waitForIdle()

        pressBack()

        completedCheckbox().assertDoesNotExist()
        composeTestRule.onReadAsOne("First.").assertExists()
    }

    // Once focus moves on, the focus target around the pages can't take it again, which would
    // stop Back.
    @Test
    fun back_afterRemovingADateAndTabbingIntoThePage_leavesThePage() {
        removeDateWithEnter()
        press(Key.Tab)
        // pressBack() doesn't wait for Compose, so let focus settle first.
        composeTestRule.waitForIdle()

        pressBack()

        completedCheckbox().assertDoesNotExist()
        composeTestRule.onReadAsOne("First.").assertExists()
    }

    // Mark completed leaves composition once a date is picked, as Change date and Unmark take its
    // place.
    @Test
    fun enterOnMarkCompleted_outOfTouchMode_focusesNothingOnceADateIsPicked() {
        openCampingOutOfTouchMode()
        pressWithEnter(composeTestRule.onNodeWithText("Mark completed"))

        composeTestRule.onNodeWithText("OK").performClick()

        composeTestRule.onNodeWithText("Unmark").assertExists()
        assertNothingFocused()
    }

    // Unmark and Change date leave composition, as Mark completed takes their place.
    @Test
    fun enterOnUnmark_outOfTouchMode_focusesNothing() {
        openCampingOutOfTouchMode()
        composeTestRule.onNodeWithText("Mark completed").performScrollTo().performClick()
        composeTestRule.onNodeWithText("OK").performClick()

        pressWithEnter(composeTestRule.onNodeWithText("Unmark"))

        composeTestRule.onNodeWithText("Mark completed").assertExists()
        assertNothingFocused()
    }

    // Clear progress leaves composition once the scout confirms.
    @Test
    fun enterOnClearProgress_outOfTouchMode_focusesNothingOnceConfirmed() {
        openRequirementOutOfTouchMode()
        completedCheckbox().performClick()
        pressWithEnter(composeTestRule.onNodeWithText("Clear progress"))

        composeTestRule.onNodeWithText("Clear").performClick()

        composeTestRule.onNodeWithText("Clear progress").assertDoesNotExist()
        assertNothingFocused()
    }

    /**
     * Out of touch mode, presses Clear all in Data management with a key and confirms. It's
     * disabled once confirmed, with nothing left to clear.
     */
    private fun clearAllWithEnter() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        runBlocking {
            progressRepository.startBadge(
                "camping",
                requirementsVersion = LocalDate.of(2026, 1, 1),
                startedDate = LocalDate.of(2026, 3, 1)
            )
        }
        launchWithProfile()
        composeTestRule.onNodeWithText("Manage data").performScrollTo().performClick()
        pressWithEnter(composeTestRule.onNodeWithText("Clear all"))

        composeTestRule.onNodeWithText("Clear").performClick()

        composeTestRule.onNodeWithText("Clear all").assertIsNotEnabled()
    }

    @Test
    fun enterOnClearAll_outOfTouchMode_focusesNothingOnceConfirmed() {
        clearAllWithEnter()

        assertNothingFocused()
    }

    // A disabled button gives up focus at once, not once the change is applied as a removed one
    // does.
    @Test
    fun tab_afterClearingAll_movesFocusIntoThePage() {
        clearAllWithEnter()

        press(Key.Tab)

        // Edit, for the scout's name and unit number.
        composeTestRule.onNodeWithText("Edit").assertIsFocused()
    }

    // From Android 9, in touch mode, Android doesn't ask the view to take focus again as a focused
    // item goes, so the focus target around the pages mustn't be left able to take it. A later key
    // press that leaves touch mode asks the view to take focus going down, and the target would
    // take it. Onboarding's fields are disabled once saved, as Home replaces the page.
    @Test
    fun leavingTouchMode_afterAFocusedFieldWasDisabled_focusesThePagesFirstItem() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
        launch()
        field("Name").performTextInput("Alex Scout")
        field("Unit number").performTextInput("123")
        field("Unit number").assertIsFocused()
        composeTestRule.onNodeWithText("Get started").performClick()
        home().assertIsDisplayed()
        assertNothingFocused()

        // As a key press does on a phone (ViewRootImpl.leaveTouchMode()). Robolectric's keys go
        // straight to Compose, and setInTouchMode() doesn't reach a window that's showing.
        scenario.onActivity { it.window.decorView.requestFocusFromTouch() }

        rankCard("Next: Scout").assertIsFocused()
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
        composeTestRule.onNodeWithText("0 of 1 completed").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun openDataManagement_showsDataManagement() {
        launchWithProfile()

        composeTestRule.onNodeWithText("Manage data").performScrollTo().performClick()

        home().assertDoesNotExist()
        composeTestRule.onNodeWithText("Data management").assertIsDisplayed()
    }

    @Test
    fun back_fromDataManagement_returnsHome() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Manage data").performScrollTo().performClick()
        // As in back_fromBadges_returnsHome, let the new entry settle before pressing back.
        composeTestRule.waitForIdle()

        pressBack()

        // Home is back where it was scrolled to: the button tapped is on screen, and the name
        // above it.
        home().assertExists()
        composeTestRule.onNodeWithText("Manage data").assertIsDisplayed()
    }

    private fun openEditNameAndUnit() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Manage data").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Edit").performClick()
    }

    private suspend fun profile() = profileRepository.observeProfile().first()

    @Test
    fun nameAndUnit_savedOnTheirPage_showOnHome() {
        openEditNameAndUnit()

        // Editing starts from what's saved.
        assertFieldText("Name", "Alex Scout")
        assertFieldText("Unit number", "123")
        field("Name").performTextReplacement("Sam Scout")
        field("Unit number").performTextReplacement("Crew 7")
        composeTestRule.onNodeWithText("Save").performClick()

        assertEquals(Profile("Sam Scout", "Crew 7"), runBlocking { profile() })
        // Saving closes the page, back to Data management.
        field("Name").assertDoesNotExist()
        composeTestRule.onNodeWithText("Data management").assertIsDisplayed()

        pressBack()

        // Home keeps where it was scrolled to, as in back_fromRanks_returnsHome.
        composeTestRule.onNode(isHeading() and hasText("Sam Scout"))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Unit: Crew 7").assertIsDisplayed()
    }

    @Test
    fun back_fromEditNameAndUnit_withChanges_asksBeforeDiscardingThem() {
        openEditNameAndUnit()
        field("Name").performTextReplacement("Sam Scout")
        composeTestRule.waitForIdle()

        pressBack()
        discardDialog().assertIsDisplayed()
        composeTestRule.onNodeWithText("Discard").performClick()

        composeTestRule.onNodeWithText("Data management").assertIsDisplayed()
        assertEquals(Profile("Alex Scout", "123"), runBlocking { profile() })
        // Opening it again starts from what's saved, not the discarded edit.
        composeTestRule.onNodeWithText("Edit").performClick()
        assertFieldText("Name", "Alex Scout")
    }

    private fun openScout() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Ranks").performScrollTo().performClick()
        composeTestRule.onReadAsOne("Scout").performClick()
    }

    @Test
    fun openRanks_showsRanks() {
        launchWithProfile()

        composeTestRule.onNodeWithText("Ranks").performScrollTo().performClick()

        home().assertDoesNotExist()
        composeTestRule.onNode(isHeading() and hasText("Ranks")).assertIsDisplayed()
        composeTestRule.onReadAsOne("Scout").assert(hasLine("In progress")).assertIsDisplayed()
        composeTestRule.onReadAsOne("Tenderfoot").assertIsDisplayed()
        composeTestRule.onNodeWithText("Camping", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun back_fromRanks_returnsHome() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Ranks").performScrollTo().performClick()
        // pressBack() doesn't wait for Compose, so let the Ranks entry settle first.
        composeTestRule.waitForIdle()

        pressBack()

        // Home is back where it was scrolled to: the button tapped is on screen, and the name
        // above it.
        home().assertExists()
        ranksButton().assertIsDisplayed()
    }

    @Test
    fun openRank_showsRankDetail_andItsRequirementsPage() {
        openScout()

        composeTestRule.onNodeWithText("Our summary of Scout.").assertIsDisplayed()
        composeTestRule.onReadAsOne("Scout's first.").performScrollTo().performClick()

        composeTestRule.onNodeWithText("Requirement 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Our summary of Scout.").assertDoesNotExist()
    }

    @Test
    fun back_fromRankDetail_returnsToRanks() {
        openScout()
        composeTestRule.waitForIdle()

        pressBack()

        composeTestRule.onReadAsOne("Tenderfoot").assertIsDisplayed()
        composeTestRule.onNodeWithText("Our summary of Scout.").assertDoesNotExist()
    }

    @Test
    fun markingRankEarned_showsOnRanks_andMovesInProgressUp() {
        openScout()

        composeTestRule.onNodeWithText("Mark earned").performScrollTo().performClick()
        composeTestRule.onNodeWithText("OK").performClick()
        composeTestRule.onNodeWithText("Earned on May 20, 2026").assertIsDisplayed()
        composeTestRule.waitForIdle()
        pressBack()

        composeTestRule.onReadAsOne("Scout").assert(hasLine("Earned")).assertIsDisplayed()
        composeTestRule.onReadAsOne("Tenderfoot").assert(hasLine("In progress"))
            .assertIsDisplayed()
    }

    @Test
    fun openRankInProgress_fromHome_showsRankDetail_andBackReturnsHome() {
        launchWithProfile()

        rankCard("Next: Scout").performClick()

        home().assertDoesNotExist()
        composeTestRule.onNodeWithText("Our summary of Scout.").assertIsDisplayed()
        composeTestRule.waitForIdle()
        pressBack()
        home().assertIsDisplayed()
    }

    @Test
    fun markingRankEarned_showsOnHome() {
        openScout()
        composeTestRule.onNodeWithText("Mark earned").performScrollTo().performClick()
        composeTestRule.onNodeWithText("OK").performClick()
        composeTestRule.waitForIdle()
        pressBack()
        composeTestRule.waitForIdle()
        pressBack()

        rankCard("Your rank. Scout. 1 of 2 ranks earned. Next: Tenderfoot. In progress")
            .assertIsDisplayed()
    }

    @Test
    fun shareReport_ofAnEarnedRank_opensShareSheetWithTheRanksReport() {
        openScout()
        composeTestRule.onNodeWithText("Mark earned").performScrollTo().performClick()
        composeTestRule.onNodeWithText("OK").performClick()

        composeTestRule.onNodeWithText("Share report").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("scout"), fakeReportRepository.shared)
        var started: Intent? = null
        scenario.onActivity { started = shadowOf(it).nextStartedActivity }
        assertEquals(Intent.ACTION_CHOOSER, started?.action)
        val send =
            IntentCompat.getParcelableExtra(started!!, Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(
            FakeReportRepository.reportUri("scout"),
            IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java)
        )
    }

    @Test
    fun openBadge_fromARequirementThatAsksForMeritBadges_showsBadgeDetail_andBackReturns() {
        runBlocking {
            progressRepository.setCompletedOnPriorDate(
                "camping",
                LocalDate.of(2025, 8, 1),
                BadgeStart(LocalDate.of(2026, 1, 1), today)
            )
        }
        launchWithProfile()
        composeTestRule.onNodeWithText("Ranks").performScrollTo().performClick()
        composeTestRule.onReadAsOne("Tenderfoot").performClick()
        composeTestRule.onReadAsOne("Earn a merit badge.").performScrollTo().performClick()

        composeTestRule.onReadAsOne("Camping").performScrollTo().performClick()

        composeTestRule.onNodeWithText("Our summary of Camping.").assertIsDisplayed()
        composeTestRule.waitForIdle()
        pressBack()
        composeTestRule.onNodeWithText("Requirement 2").assertIsDisplayed()
    }

    @Test
    fun doubleTap_onRanks_opensRanksOnce() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Ranks").performScrollTo()

        tapTwiceInOneFrame("Ranks")
        pressBack()

        // Home is back where it was scrolled to, as in back_fromRanks_returnsHome.
        home().assertExists()
        ranksButton().assertIsDisplayed()
    }

    @Test
    fun doubleTap_onRank_opensItOnce() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Ranks").performScrollTo().performClick()

        tapTwiceInOneFrame(composeTestRule.onReadAsOne("Scout"))
        pressBack()

        composeTestRule.onReadAsOne("Tenderfoot").assertIsDisplayed()
    }

    @Test
    fun doubleTap_onRankInProgress_opensItOnce() {
        launchWithProfile()

        tapTwiceInOneFrame(rankCard("Next: Scout"))
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

        composeTestRule.onNodeWithText("Manage data").performScrollTo()
        tapTwiceInOneFrame("Manage data")
        pressBack()

        // Home is back where it was scrolled to, as in back_fromDataManagement_returnsHome.
        home().assertExists()
        composeTestRule.onNodeWithText("Manage data").assertIsDisplayed()
    }

    @Test
    fun doubleTap_onEditNameAndUnit_opensItOnce() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Manage data").performScrollTo().performClick()

        tapTwiceInOneFrame("Edit")
        pressBack()

        composeTestRule.onNodeWithText("Data management").assertIsDisplayed()
    }

    @Test
    fun doubleTap_onBadgeInProgress_opensItOnce() {
        runBlocking {
            progressRepository.startBadge(
                "camping",
                requirementsVersion = LocalDate.of(2026, 1, 1),
                startedDate = LocalDate.of(2026, 3, 1)
            )
        }
        launchWithProfile()
        composeTestRule.onReadAsOne("Camping").performScrollTo()

        tapTwiceInOneFrame(composeTestRule.onReadAsOne("Camping"))
        pressBack()

        assertHomeBackAtCamping()
    }

    @Test
    fun doubleTap_onBadge_opensItOnce() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Merit badges").performClick()

        // Let the list finish animating in. Badge detail's heading also says "Camping", so
        // match the list's row.
        val camping = campingRow()
            .assertIsDisplayed()

        // Tap a second time while the list is still sliding out, as a quick double tap
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
        val camping = campingRow()
            .assertIsDisplayed()

        // The second tap of a quick double tap lands on Badge detail, which is sliding in,
        // 100 ms after the first. Which control is under the finger depends on the
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

        // After Back, Badge detail slides away beside Badges. Tap its link while it's still
        // on screen.
        composeTestRule.mainClock.autoAdvance = false
        pressBack()
        // Under Robolectric, Espresso doesn't wait for Compose, and nothing else in this test
        // runs Compose's coroutines before the tap, so hand the back stack change to Compose
        // now. With the clock stopped, this doesn't let time pass.
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(100)
        // Badges is sliding in beside Badge detail.
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
    fun tap_onPageReturnedTo_afterDoubleTapTimeout_worksWhileClosingPageFinishes() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Manage data").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Data management slides away beside Home, so once Home's 300 ms double-tap timeout
        // has passed, Home takes a tap where Data management no longer is, before the 375 ms
        // slide ends.
        composeTestRule.mainClock.autoAdvance = false
        pressBack()
        // Hand the back stack change to Compose; see tap_onClosingScreenAfterBack_doesNothing.
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(350)
        composeTestRule.onNodeWithText("Data management").assertExists()
        composeTestRule.onNodeWithText("Merit badges").performClick()
        composeTestRule.mainClock.autoAdvance = true

        // Badges' title matches Home's button, so check for its row.
        campingRow().assertIsDisplayed()
        home().assertDoesNotExist()
    }

    @Test
    fun doubleTap_onRequirement_opensItOnce() {
        openCamping()
        composeTestRule.onReadAsOne("Second.").performScrollTo()

        tapTwiceInOneFrame(composeTestRule.onReadAsOne("Second."))
        pressBack()

        // Back on Badge detail, where it was left: scrolled down to the requirement. A second
        // copy of its page would still show.
        composeTestRule.onReadAsOne("Second.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Our summary of Camping.").assertExists()
        composeTestRule.onNodeWithText("Requirement 2").assertDoesNotExist()
    }

    @Test
    fun doubleTap_onSubRequirement_opensItOnce() {
        openCamping()
        composeTestRule.onReadAsOne("Second.").performScrollTo().performClick()

        tapTwiceInOneFrame(composeTestRule.onReadAsOne("Choice B."))
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

        // Tap a badge 350 ms after opening the list, after the 300 ms double-tap timeout,
        // while it is still sliding in.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithText("Merit badges").performClick()
        composeTestRule.mainClock.advanceTimeBy(350)
        home().assertExists()
        composeTestRule.onReadAsOne("Camping").performClick()
        composeTestRule.mainClock.autoAdvance = true

        composeTestRule.onNodeWithText("Our summary of Camping.").assertIsDisplayed()
    }

    @Test
    fun tap_onBadgesLeavingForOnboarding_doesNothing() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Merit badges").performClick()
        val camping = composeTestRule.onReadAsOne("Camping").assertIsDisplayed()

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

    // Badge detail's heading also says "Camping", so match the list's row.
    private fun campingRow() = composeTestRule.onReadAsOne("Camping")

    @Test
    fun openingPage_slidesItInFromTheRight_andThePageLeftSlidesLeft() {
        launchWithProfile()
        val homeAtRest = home().getBoundsInRoot()

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithText("Merit badges").performClick()
        composeTestRule.mainClock.advanceTimeBy(100)
        val homeMoving = home().getBoundsInRoot()
        val badgesMoving = campingRow().getBoundsInRoot()
        composeTestRule.mainClock.autoAdvance = true
        val badgesAtRest = campingRow().getBoundsInRoot()

        assertTrue(badgesMoving.left > badgesAtRest.left)
        assertTrue(homeMoving.left < homeAtRest.left)
    }

    @Test
    fun back_slidesThePageRight_andThePageReturnedToSlidesInFromTheLeft() {
        launchWithProfile()
        val homeAtRest = home().getBoundsInRoot()
        composeTestRule.onNodeWithText("Merit badges").performClick()
        val badgesAtRest = campingRow().getBoundsInRoot()

        composeTestRule.mainClock.autoAdvance = false
        pressBack()
        // Hand the back stack change to Compose; see tap_onClosingScreenAfterBack_doesNothing.
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(100)
        val badgesMoving = campingRow().getBoundsInRoot()
        val homeMoving = home().getBoundsInRoot()
        composeTestRule.mainClock.autoAdvance = true

        assertTrue(badgesMoving.left > badgesAtRest.left)
        assertTrue(homeMoving.left < homeAtRest.left)
    }

    /**
     * Starts a back gesture from [edge], and holds it halfway across. A phone sends every gesture
     * through one input, so a later swipe can pass the [input] an earlier one returned.
     */
    private fun swipeHalfwayBack(
        edge: Int = NavigationEvent.EDGE_LEFT,
        input: DirectNavigationEventInput? = null
    ): DirectNavigationEventInput {
        val gesture = input ?: DirectNavigationEventInput()
        scenario.onActivity {
            if (input == null) it.navigationEventDispatcher.addInput(gesture)
            gesture.backStarted(NavigationEvent(edge, progress = 0f))
            gesture.backProgressed(NavigationEvent(edge, progress = 0.5f))
        }
        composeTestRule.waitForIdle()
        return gesture
    }

    /** Checks that a back swipe hasn't moved Badges, or started drawing Home. */
    private fun assertPagesNotMoved(badgesAtRest: DpRect) {
        assertEquals(badgesAtRest, campingRow().getBoundsInRoot())
        home().assertDoesNotExist()
    }

    @Test
    fun backGesture_doesNotMoveThePages() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Merit badges").performClick()
        val badgesAtRest = campingRow().getBoundsInRoot()

        swipeHalfwayBack()

        assertPagesNotMoved(badgesAtRest)
    }

    @Test
    fun backGesture_fromTheRightEdge_doesNotMoveThePagesEither() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Merit badges").performClick()
        val badgesAtRest = campingRow().getBoundsInRoot()

        swipeHalfwayBack(NavigationEvent.EDGE_RIGHT)

        assertPagesNotMoved(badgesAtRest)
    }

    @Test
    fun backGesture_cancelled_leavesThePageWhereItWas() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Merit badges").performClick()
        val badgesAtRest = campingRow().getBoundsInRoot()

        val gesture = swipeHalfwayBack()
        scenario.onActivity { gesture.backCancelled() }

        assertPagesNotMoved(badgesAtRest)
    }

    @Test
    fun backGesture_released_slidesToThePreviousPageAsBackDoes() {
        launchWithProfile()
        val homeAtRest = home().getBoundsInRoot()
        composeTestRule.onNodeWithText("Merit badges").performClick()
        val badgesAtRest = campingRow().getBoundsInRoot()

        val gesture = swipeHalfwayBack()
        composeTestRule.mainClock.autoAdvance = false
        scenario.onActivity { gesture.backCompleted() }
        // Hand the back stack change to Compose; see tap_onClosingScreenAfterBack_doesNothing.
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(100)
        val badgesMoving = campingRow().getBoundsInRoot()
        val homeMoving = home().getBoundsInRoot()
        composeTestRule.mainClock.autoAdvance = true

        assertTrue(badgesMoving.left > badgesAtRest.left)
        assertTrue(homeMoving.left < homeAtRest.left)
        assertEquals(homeAtRest, home().getBoundsInRoot())
        campingRow().assertDoesNotExist()
    }

    @Test
    fun backGesture_released_onAPageWithChanges_asksAndLeavesThePageWhereItWas() {
        typeAnUnsavedNight()
        val fieldAtRest = weatherField().getBoundsInRoot()

        val gesture = swipeHalfwayBack()
        discardDialog().assertDoesNotExist()
        scenario.onActivity { gesture.backCompleted() }

        discardDialog().assertIsDisplayed()
        assertEquals(fieldAtRest, weatherField().getBoundsInRoot())
    }

    @Test
    fun backGesture_onAPageWithChanges_asksAgainAfterCancel() {
        typeAnUnsavedNight()
        // The first swipe completes without closing the page, so the second must still reach Back.
        val gesture = swipeHalfwayBack()
        scenario.onActivity { gesture.backCompleted() }
        composeTestRule.onNodeWithText("Cancel").performClick()
        discardDialog().assertDoesNotExist()

        swipeHalfwayBack(input = gesture)
        scenario.onActivity { gesture.backCompleted() }

        discardDialog().assertIsDisplayed()
        assertFieldText("Weather", "Rained all night.")
    }

    @Test
    fun twoBacksBeforeTheNextFrame_stopAtHome() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Merit badges").performClick()
        campingRow().assertIsDisplayed()

        // The second Back arrives before a frame updates whether the app handles Back.
        scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
            it.onBackPressedDispatcher.onBackPressed()
        }

        home().assertIsDisplayed()
    }

    private fun welcome() = composeTestRule.onNodeWithText("Welcome to BlueCard")

    // Onboarding and the back stack replace each other whole, so neither slides in as a page
    // opened from the other.
    @Test
    fun profileRemoved_fadesToOnboarding_withoutSliding() {
        launchWithProfile()
        composeTestRule.onNodeWithText("Merit badges").performClick()
        val badgesAtRest = campingRow().getBoundsInRoot()

        composeTestRule.mainClock.autoAdvance = false
        fakeProfileRepository.removeProfile()
        composeTestRule.mainClock.advanceTimeBy(100)
        val badgesFading = campingRow().getBoundsInRoot()
        val welcomeFading = welcome().getBoundsInRoot()
        composeTestRule.mainClock.autoAdvance = true

        assertEquals(badgesAtRest, badgesFading)
        assertEquals(welcome().getBoundsInRoot(), welcomeFading)
    }

    @Test
    fun profileSaved_fadesFromOnboarding_withoutSliding() {
        launch()
        val welcomeAtRest = welcome().assertIsDisplayed().getBoundsInRoot()

        composeTestRule.mainClock.autoAdvance = false
        runBlocking { profileRepository.saveProfile(Profile("Alex Scout", "123")) }
        composeTestRule.mainClock.advanceTimeBy(100)
        val welcomeFading = welcome().getBoundsInRoot()
        composeTestRule.mainClock.autoAdvance = true

        assertEquals(welcomeAtRest, welcomeFading)
        home().assertIsDisplayed()
    }

    // Pages are laid out inside the system bars' insets, so a page sliding past the side of
    // that area would draw under a navigation bar or cutout at the side, as in landscape.
    @Test
    fun openingPage_slidesOnlyInsideTheAreaForPages() {
        launchWithProfile()
        // A navigation bar at the right, as 3-button navigation is in landscape.
        val navigationBar = 100.dp
        val barPixels = with(composeTestRule.density) { navigationBar.roundToPx() }
        scenario.onActivity {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, barPixels, 0))
                .build()
            ViewCompat.dispatchApplyWindowInsets(it.window.decorView, insets)
        }
        val areaRight = composeTestRule.onRoot().getBoundsInRoot().right - navigationBar

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithText("Merit badges").performClick()
        composeTestRule.mainClock.advanceTimeBy(100)
        val badgesMoving = campingRow().getBoundsInRoot()
        composeTestRule.mainClock.autoAdvance = true
        val badgesAtRest = campingRow().getBoundsInRoot()

        // The row reaches the side of the area, so it would slide past it.
        assertEquals(areaRight.value, badgesAtRest.right.value, 1f)
        assertTrue(badgesMoving.right <= areaRight)
    }
}
