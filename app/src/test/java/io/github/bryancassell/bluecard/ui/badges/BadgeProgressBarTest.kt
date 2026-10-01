package io.github.bryancassell.bluecard.ui.badges

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BadgeProgressBarTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun descriptionsOf(vararg fractions: Float): List<String> {
        lateinit var descriptions: List<String>
        composeTestRule.setContent { descriptions = fractions.map { percentDoneDescription(it) } }
        composeTestRule.waitForIdle()
        return descriptions
    }

    @Test
    fun percentDoneDescription_roundsToAWholePercent() {
        assertEquals(
            listOf("40% done", "33% done", "67% done"),
            descriptionsOf(0.4f, 1f / 3, 2f / 3)
        )
    }

    @Test
    fun percentDoneDescription_isZeroOnlyWhenNothingIsDone() {
        assertEquals(listOf("0% done", "1% done"), descriptionsOf(0f, 0.001f))
    }

    @Test
    fun percentDoneDescription_isNeverAHundred_whileSomethingIsLeft() {
        assertEquals(listOf("99% done"), descriptionsOf(0.999f))
    }
}
