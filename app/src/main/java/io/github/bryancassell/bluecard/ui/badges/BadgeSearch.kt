package io.github.bryancassell.bluecard.ui.badges

import io.github.bryancassell.bluecard.data.catalog.MeritBadge

private val whitespace = Regex("\\s+")

/**
 * Whether this badge matches a search for [query]: every word of the query starts a word in
 * the badge's name or summary, in any order and ignoring case. "fit" and "fitness personal"
 * match Personal Fitness, but "art" doesn't match a summary just because it says "part". A
 * blank query matches every badge.
 */
fun MeritBadge.matchesSearch(query: String): Boolean =
    query.split(whitespace).filter { it.isNotEmpty() }.all { word ->
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
