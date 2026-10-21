package io.github.bryancassell.bluecard.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Content shown without them, as in a test that sets its content without setContentWithText,
// fails at once and says what provides them.
@RunWith(AndroidJUnit4::class)
class LocalTextTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun localeFormats_notProvided_failsSayingWhatProvidesThem() {
        val error = assertThrows(IllegalStateException::class.java) {
            composeTestRule.setContent { LocalLocaleFormats.current }
        }

        assertEquals(
            "LocalLocaleFormats isn't provided: ProvideStringsLanguageResources provides it",
            error.message
        )
    }

    @Test
    fun textRules_notProvided_failsSayingWhatProvidesThem() {
        val error = assertThrows(IllegalStateException::class.java) {
            composeTestRule.setContent { LocalTextRules.current }
        }

        assertEquals("LocalTextRules isn't provided: BlueCardApp provides it", error.message)
    }
}
