package io.github.bryancassell.bluecard.ui.badges

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeSearchTest {
    private fun badge(name: String, summary: String) = MeritBadge(
        id = name.lowercase().replace(' ', '-'),
        name = name,
        summary = summary,
        officialUrl = "https://www.scouting.org/merit-badges/",
        eagleRequired = false,
        eagleGroup = null,
        requirementVersions = listOf(
            RequirementsVersion(LocalDate.of(2026, 1, 1), listOf(Requirement("1", "First.")))
        )
    )

    private fun MeritBadge.matches(query: String) = matchesSearch(searchWords(query))

    private val fitness = badge(
        "Personal Fitness",
        "Build a lifelong habit of exercise, and track your progress for twelve weeks."
    )
    private val signs = badge(
        "Signs, Signals, and Codes",
        "Send and read messages with flags, lights and sounds."
    )

    @Test
    fun searchWords_areRunsOfLettersAndDigits() {
        assertEquals(
            listOf("Signs", "signals", "3", "D", "scout", "s"),
            searchWords("  Signs, signals 3-D. scout\u2019s ")
        )
    }

    @Test
    fun searchWords_ofOnlySpacesAndPunctuation_isEmpty() {
        assertEquals(emptyList<String>(), searchWords(""))
        assertEquals(emptyList<String>(), searchWords(" ., - "))
    }

    @Test
    fun noWords_matchesEveryBadge() {
        assertTrue(fitness.matches(""))
        assertTrue(fitness.matches("   "))
        assertTrue(fitness.matches("..."))
    }

    @Test
    fun wholeWordOfName_matches() {
        assertTrue(fitness.matches("Fitness"))
    }

    @Test
    fun startOfWordInName_matches() {
        assertTrue(fitness.matches("fit"))
        assertTrue(fitness.matches("pers"))
    }

    @Test
    fun wordFromSummary_matches() {
        assertTrue(fitness.matches("exercise"))
        assertTrue(fitness.matches("lifelong"))
    }

    @Test
    fun ignoresCase() {
        assertTrue(fitness.matches("FITNESS"))
        assertTrue(fitness.matches("eXeRcIsE"))
    }

    @Test
    fun middleOfWord_doesNotMatch() {
        // "ness" is in "Fitness", and "rack" is in "track".
        assertFalse(fitness.matches("ness"))
        assertFalse(fitness.matches("rack"))
    }

    @Test
    fun laterOccurrenceAtStartOfWord_matches() {
        // The first "ex" is inside "Flexible"; the second starts "exercise".
        val stretching = badge("Stretching", "Flexible joints, and exercise to keep them.")

        assertTrue(stretching.matches("ex"))
    }

    @Test
    fun wordAfterPunctuation_matches() {
        assertTrue(signs.matches("signals"))
        assertTrue(signs.matches("codes"))
    }

    @Test
    fun everyWordMustMatch_inAnyOrder() {
        assertTrue(fitness.matches("personal fitness"))
        assertTrue(fitness.matches("fitness personal"))
        assertTrue(signs.matches("signs signals"))
        assertFalse(fitness.matches("personal cooking"))
    }

    @Test
    fun wordsCanMatchNameAndSummaryTogether() {
        assertTrue(fitness.matches("fitness weeks"))
    }

    @Test
    fun extraSpaces_areIgnored() {
        assertTrue(fitness.matches("  personal   fitness  "))
    }

    // A keyboard can add a period after a double space.
    @Test
    fun punctuationInQuery_isIgnored() {
        assertTrue(fitness.matches("fitness."))
        assertTrue(fitness.matches("personal, fitness"))
    }

    @Test
    fun otherSpaces_separateWords() {
        // A no-break space, as pasted text can have, and an ideographic space.
        assertTrue(fitness.matches("personal\u00A0fitness"))
        assertTrue(fitness.matches("personal\u3000fitness"))
    }

    @Test
    fun straightAndCurlyApostrophes_matchEachOther() {
        // No other word starts with "s", so it can only match after the apostrophe.
        val straight = badge("Oceanography", "Explore the world's oceans.")
        val curly = badge("Oceanography", "Explore the world\u2019s oceans.")

        assertTrue(straight.matches("world\u2019s"))
        assertTrue(curly.matches("world's"))
    }

    // U+20000, a CJK letter written as two UTF-16 surrogates.
    private val supplementaryLetter = "\uD840\uDC00"

    @Test
    fun letterOutsideBasicPlane_isPartOfTheWord() {
        val badge = badge("Stamps", "Collect ${supplementaryLetter}fit stamps.")

        assertEquals(listOf("${supplementaryLetter}fit"), searchWords("${supplementaryLetter}fit"))
        assertFalse(badge.matches("fit"))
        assertTrue(badge.matches("${supplementaryLetter}fit"))
    }

    @Test
    fun unrelatedWord_doesNotMatch() {
        assertFalse(fitness.matches("cooking"))
    }
}
