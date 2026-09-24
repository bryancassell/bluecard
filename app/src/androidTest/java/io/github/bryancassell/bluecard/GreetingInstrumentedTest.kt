package io.github.bryancassell.bluecard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented test: runs on an emulator or connected device. */
@RunWith(AndroidJUnit4::class)
class GreetingInstrumentedTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun greeting_isDisplayed() {
        composeTestRule.setContent { Greeting("Scout") }
        composeTestRule.onNodeWithText("Welcome to BlueCard, Scout!").assertIsDisplayed()
    }
}
