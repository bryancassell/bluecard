package io.github.bryancassell.bluecard.ui.data

import androidx.compose.ui.test.junit4.v2.createComposeRule
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
 * The Merge dialog's system bar icons show on its page (#299): dark on the light page, light on
 * the dark one.
 */
@RunWith(AndroidJUnit4::class)
// Android 8, where the dialog's window otherwise has white navigation bar icons on the light page,
// and the two later ways WindowInsetsControllerCompat sets them: from Android 11 and Android 15.
@Config(sdk = [26, 30, 36])
class MergeDialogSystemBarsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val backup = Backup(Profile("Sam Scout", "Crew 7"), emptyList())

    // A "light" bar is one with dark icons.
    private fun assertMergeDialogHasLightBars(light: Boolean) {
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
        val window = checkNotNull(ShadowDialog.getLatestDialog().window)
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        assertEquals(light, bars.isAppearanceLightStatusBars)
        assertEquals(light, bars.isAppearanceLightNavigationBars)
    }

    @Test
    @Config(qualifiers = "notnight")
    fun lightMode_hasDarkIcons() {
        assertMergeDialogHasLightBars(light = true)
    }

    // Under Robolectric, light icons are also a dialog window's default, so this can't catch the
    // icons not being set, only dark ones set in dark mode.
    @Test
    @Config(qualifiers = "night")
    fun darkMode_hasLightIcons() {
        assertMergeDialogHasLightBars(light = false)
    }
}
