package io.github.bryancassell.bluecard.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import io.github.bryancassell.bluecard.text.AndroidTextRules
import io.github.bryancassell.bluecard.ui.LocalLocaleFormats
import io.github.bryancassell.bluecard.ui.LocalTextRules
import io.github.bryancassell.bluecard.ui.rememberLocaleFormats

/**
 * Provides the text formats and rules that `BlueCardApp` provides to [content], for a test that
 * shows part of the app without it: Android's, with the formats in the strings' locale, as on a
 * phone.
 */
@Composable
fun ProvideText(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalTextRules provides remember { AndroidTextRules() },
        LocalLocaleFormats provides rememberLocaleFormats(),
        content = content
    )
}

/**
 * Sets [content] as the test's content inside [ProvideText]. A test that shows part of the app,
 * rather than all of it through `BlueCardApp`, sets its content with this: the screens read the
 * text formats and rules from CompositionLocals that have no default.
 */
fun ComposeContentTestRule.setContentWithText(content: @Composable () -> Unit) =
    setContent { ProvideText(content) }
