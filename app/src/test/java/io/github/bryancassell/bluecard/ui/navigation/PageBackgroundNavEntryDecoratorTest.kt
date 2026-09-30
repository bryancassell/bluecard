package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

// Draws for real, so the test can read the page's pixels.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class PageBackgroundNavEntryDecoratorTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun page_isPaintedWithTheThemeBackground() {
        composeTestRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme(background = Color.Blue)) {
                // Red shows wherever the page is see-through.
                Box(Modifier.size(100.dp).background(Color.Red).testTag("behind")) {
                    NavDisplay(
                        backStack = listOf<NavKey>(Home),
                        onBack = {},
                        entryDecorators = listOf(rememberPageBackgroundNavEntryDecorator()),
                        entryProvider = entryProvider { entry<Home> { Text("Home") } }
                    )
                }
            }
        }

        val pixels = composeTestRule.onNodeWithTag("behind").captureToImage().toPixelMap()

        // The corner away from the text.
        assertEquals(Color.Blue, pixels[pixels.width - 1, pixels.height - 1])
    }
}
