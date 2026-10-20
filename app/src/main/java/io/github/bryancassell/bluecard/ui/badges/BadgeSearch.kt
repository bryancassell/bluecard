package io.github.bryancassell.bluecard.ui.badges

import io.github.bryancassell.bluecard.data.catalog.MeritBadge

// A letter or digit: the code points that Java's Character.isLetterOrDigit() accepts. Regex,
// unlike Character, is in Kotlin's common library, which code shared with iOS can use (#341).
private val wordCharacter = Regex("[\\p{L}\\p{Nd}]")

// Anything but a letter or digit.
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
        // By code point, as searchWords splits, so a letter outside the Basic Multilingual
        // Plane counts as one letter rather than two surrogates.
        if (index == 0 || !wordCharacter.matchesAt(this, codePointStartBefore(index))) return true
        index = indexOf(prefix, startIndex = index + 1, ignoreCase = true)
    }
    return false
}

/** Where the code point before [index] starts: one char back, or two for a surrogate pair. */
private fun String.codePointStartBefore(index: Int): Int =
    if (index >= 2 && this[index - 1].isLowSurrogate() && this[index - 2].isHighSurrogate()) {
        index - 2
    } else {
        index - 1
    }
