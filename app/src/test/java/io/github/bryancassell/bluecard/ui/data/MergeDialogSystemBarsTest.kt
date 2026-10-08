package io.github.bryancassell.bluecard.ui.data

import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.backup.Backup
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.ui.theme.BlueCardTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * The Merge dialog's system bar icons are the page's under it (#299): its window takes the ones
 * that enableEdgeToEdge() gives the activity's.
 */
@RunWith(AndroidJUnit4::class)
// Android 8, where the dialog's window otherwise has white navigation bar icons on the light page.
@Config(sdk = [26])
class MergeDialogSystemBarsTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val backup = Backup(Profile("Sam Scout", "Crew 7"), emptyList())

    /** Shows the dialog over a page set up as MainActivity's, and returns its window. */
    private fun showMergeDialog(): Window {
        composeTestRule.runOnUiThread { composeTestRule.activity.enableEdgeToEdge() }
        composeTestRule.setContent {
            BlueCardTheme {
                MergeDialog(
                    MergeChoices(
                        MergeSources(emptyList(), emptyList(), backup, backup),
                        ProfileChoice(
                            Profile("Sam Scout", "Crew 7"),
                            Profile("Sam Lee", "Troop 12")
                        ),
                        emptyList()
                    ),
                    onChooseProfile = {},
                    onChooseProgress = { _, _ -> },
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        return checkNotNull(ShadowDialog.getLatestDialog().window)
    }

    // A "light" bar is one with dark icons.
    private fun assertHasThePagesIcons(dialogWindow: Window, light: Boolean) {
        val pageWindow = composeTestRule.activity.window
        val page = WindowCompat.getInsetsController(pageWindow, pageWindow.decorView)
        val dialog = WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView)
        // The page's are what enableEdgeToEdge() chose, so the dialog's match something real.
        assertEquals(light, page.isAppearanceLightStatusBars)
        assertEquals(light, page.isAppearanceLightNavigationBars)
        assertEquals(light, dialog.isAppearanceLightStatusBars)
        assertEquals(light, dialog.isAppearanceLightNavigationBars)
    }

    @Test
    @Config(qualifiers = "notnight")
    fun lightMode_hasThePagesDarkIcons() {
        assertHasThePagesIcons(showMergeDialog(), light = true)
    }

    @Test
    @Config(qualifiers = "night")
    fun darkMode_hasThePagesLightIcons() {
        assertHasThePagesIcons(showMergeDialog(), light = false)
    }
}
