package io.github.bryancassell.bluecard.ui.badges

import io.github.bryancassell.bluecard.data.catalog.MeritBadge

// Anything but a letter or digit, the characters that isLetterOrDigit() rejects.
private val nonWordCharacters = Regex("[^\\p{L}\\p{Nd}]+")

/**
 * The words of a search: its runs of letters and digits. Spaces and punctuation separate
 * words, as they do in badge text, so "fitness." searches for "fitness".
 */
fun searchWords(query: String): List<String> =
    query.split(nonWordCharacters).filter { it.isNotEmpty() }

/**
 * Whether this badge matches a search for [words], from [searchWords]: every word starts a
 * word in the badge's name or summary, in any order and ignoring case. "fit" and "fitness
 * personal" match Personal Fitness, but "art" doesn't match a summary just because it says
 * "part". A search with no words matches every badge.
 */
fun MeritBadge.matchesSearch(words: List<String>): Boolean = words.all { word ->
    name.hasWordStartingWith(word) || summary.hasWordStartingWith(word)
}

/** Whether [prefix] appears at the start of a word in this text, ignoring case. */
private fun String.hasWordStartingWith(prefix: String): Boolean {
    var index = indexOf(prefix, ignoreCase = true)
    while (index >= 0) {
        if (index == 0 || !this[index - 1].isLetterOrDigit()) return true
        index = indexOf(prefix, startIndex = index + 1, ignoreCase = true)
    }
    return false
}
