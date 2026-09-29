package io.github.bryancassell.bluecard.ui.badges

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import java.time.LocalDate
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

    private val fitness = badge(
        "Personal Fitness",
        "Build a lifelong habit of exercise, and track your progress for twelve weeks."
    )
    private val signs = badge(
        "Signs, Signals, and Codes",
        "Send and read messages with flags, lights and sounds."
    )

    @Test
    fun blankQuery_matchesEveryBadge() {
        assertTrue(fitness.matchesSearch(""))
        assertTrue(fitness.matchesSearch("   "))
    }

    @Test
    fun wholeWordOfName_matches() {
        assertTrue(fitness.matchesSearch("Fitness"))
    }

    @Test
    fun startOfWordInName_matches() {
        assertTrue(fitness.matchesSearch("fit"))
        assertTrue(fitness.matchesSearch("pers"))
    }

    @Test
    fun wordFromSummary_matches() {
        assertTrue(fitness.matchesSearch("exercise"))
        assertTrue(fitness.matchesSearch("lifelong"))
    }

    @Test
    fun ignoresCase() {
        assertTrue(fitness.matchesSearch("FITNESS"))
        assertTrue(fitness.matchesSearch("eXeRcIsE"))
    }

    @Test
    fun middleOfWord_doesNotMatch() {
        // "ness" is in "Fitness", and "rack" is in "track".
        assertFalse(fitness.matchesSearch("ness"))
        assertFalse(fitness.matchesSearch("rack"))
    }

    @Test
    fun laterOccurrenceAtStartOfWord_matches() {
        // The first "ex" is inside "Flexible"; the second starts "exercise".
        val stretching = badge("Stretching", "Flexible joints, and exercise to keep them.")

        assertTrue(stretching.matchesSearch("ex"))
    }

    @Test
    fun wordAfterPunctuation_matches() {
        assertTrue(signs.matchesSearch("signals"))
        assertTrue(signs.matchesSearch("codes"))
    }

    @Test
    fun everyWordMustMatch_inAnyOrder() {
        assertTrue(fitness.matchesSearch("personal fitness"))
        assertTrue(fitness.matchesSearch("fitness personal"))
        assertTrue(signs.matchesSearch("signs signals"))
        assertFalse(fitness.matchesSearch("personal cooking"))
    }

    @Test
    fun wordsCanMatchNameAndSummaryTogether() {
        assertTrue(fitness.matchesSearch("fitness weeks"))
    }

    @Test
    fun extraSpaces_areIgnored() {
        assertTrue(fitness.matchesSearch("  personal   fitness  "))
    }

    @Test
    fun unrelatedWord_doesNotMatch() {
        assertFalse(fitness.matchesSearch("cooking"))
    }
}
