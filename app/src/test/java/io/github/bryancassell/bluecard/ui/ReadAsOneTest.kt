package io.github.bryancassell.bluecard.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadAsOneTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun label_joinsItsPartsInTurn_leavingOutNulls() {
        var label = ""
        composeTestRule.setContent {
            label = readAsOneLabel("Hiking", null, "In progress")
        }

        assertEquals("Hiking. In progress", label)
    }

    @Test
    fun partsJoin_withTheSeparator() {
        assertEquals("Week 2. 20", joinedAsOne(listOf("Week 2", "20"), ". "))
    }

    @Test
    fun partEndingASentence_isFollowedByTheSeparatorWithoutItsPunctuation() {
        assertEquals(
            "2. Tie two knots. (2 of 7 complete). Is it done? Yes! Done",
            joinedAsOne(
                listOf("2", "Tie two knots.", "(2 of 7 complete)", "Is it done?", "Yes!", "Done"),
                ". "
            )
        )
    }

    @Test
    fun lastPartEndingASentence_isKeptAsItIs() {
        assertEquals("Next. Done.", joinedAsOne(listOf("Next", "Done."), ". "))
    }

    @Test
    fun emptyPart_isJoinedLikeAnyOther() {
        assertEquals(". Done", joinedAsOne(listOf("", "Done"), ". "))
    }

    @Test
    fun onePart_isTheLabel() {
        assertEquals("Camping", joinedAsOne(listOf("Camping"), ". "))
    }
}
