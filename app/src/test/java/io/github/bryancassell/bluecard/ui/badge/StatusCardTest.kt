package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.ui.theme.BlueCardTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Checks that a status card's text is body text in light and dark mode. Its container is the
 * dark scheme's surfaceVariant color too, which Material would give secondary text.
 */
@RunWith(AndroidJUnit4::class)
class StatusCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun assertContentIsOnSurface() {
        var onSurface = Color.Unspecified
        var contentColor = Color.Unspecified
        composeTestRule.setContent {
            BlueCardTheme {
                onSurface = MaterialTheme.colorScheme.onSurface
                StatusCard { contentColor = LocalContentColor.current }
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(onSurface, contentColor)
    }

    @Test
    @Config(qualifiers = "notnight")
    fun lightMode_contentIsOnSurface() {
        assertContentIsOnSurface()
    }

    @Test
    @Config(qualifiers = "night")
    fun darkMode_contentIsOnSurface() {
        assertContentIsOnSurface()
    }
}
