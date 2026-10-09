package io.github.bryancassell.bluecard.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.text.typedText
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun partEndingWithTheSeparatorsPunctuation_isFollowedByItsSpaceOnly() {
        assertEquals(
            "2. Tie two knots. (2 of 7 complete). Done",
            joinedAsOne(listOf("2", "Tie two knots.", "(2 of 7 complete)", "Done"), ". ")
        )
    }

    // typedText wraps a right-to-left name in direction marks, which follow its final period.
    @Test
    fun partEndingWithTheSeparatorsPunctuation_thenDirectionMarks_isFollowedByItsSpaceOnly() {
        val name = typedText("\u05D3\u05D5\u05D3.", Locale.ENGLISH)
        assertFalse(name.endsWith("."))

        assertEquals("$name Unit: 12", joinedAsOne(listOf(name, "Unit: 12"), ". "))
    }

    // As a translation's separator might be.
    @Test
    fun separatorWithoutASpace_isLeftOutAfterItsPunctuation() {
        assertEquals("一。二。三", joinedAsOne(listOf("一。", "二", "三"), "。"))
    }

    @Test
    fun lastPartEndingWithTheSeparatorsPunctuation_isKeptAsItIs() {
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
