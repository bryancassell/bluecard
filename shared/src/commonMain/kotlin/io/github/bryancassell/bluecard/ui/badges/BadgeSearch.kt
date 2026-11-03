package io.github.bryancassell.bluecard.ui.badges

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.text.TextRules
import io.github.bryancassell.bluecard.text.codePointAt
import io.github.bryancassell.bluecard.text.isSurrogatePairAt

/**
 * The words of a search: its runs of letters and digits. Spaces and punctuation separate
 * words, as they do in badge text, so "fitness." searches for "fitness".
 */
fun searchWords(query: String, textRules: TextRules): List<String> {
    val words = mutableListOf<String>()
    var wordStart = -1
    var index = 0
    while (index < query.length) {
        val inWord = query.isWordCharacterAt(index, textRules)
        if (inWord && wordStart < 0) {
            wordStart = index
        } else if (!inWord && wordStart >= 0) {
            words += query.substring(wordStart, index)
            wordStart = -1
        }
        index += if (isSurrogatePairAt(query, index)) 2 else 1
    }
    if (wordStart >= 0) words += query.substring(wordStart)
    return words
}

/**
 * Whether this badge matches a search for [words], from [searchWords]: every word starts a
 * word in the badge's name or summary, in any order and ignoring case. "fit" and "fitness
 * personal" match Personal Fitness, but "art" doesn't match a summary just because it says
 * "part". A search with no words matches every badge.
 */
fun MeritBadge.matchesSearch(words: List<String>, textRules: TextRules): Boolean =
    words.all { word ->
        name.hasWordStartingWith(word, textRules) || summary.hasWordStartingWith(word, textRules)
    }

/** Whether [prefix] appears at the start of a word in this text, ignoring case. */
private fun String.hasWordStartingWith(prefix: String, textRules: TextRules): Boolean {
    var index = indexOf(prefix, ignoreCase = true)
    while (index >= 0) {
        if (index == 0 || !isWordCharacterBefore(index, textRules)) return true
        index = indexOf(prefix, startIndex = index + 1, ignoreCase = true)
    }
    return false
}

// Characters are read by code point, so a letter beyond the Basic Multilingual Plane is one
// letter rather than two surrogates. A regex of letters (\p{L}) can't be used: Kotlin/Native's
// sees the two surrogates, so on iOS it split U+20000, a CJK letter, from the "fit" after it.
// Only a surrogate pair goes to the platform, since Char.isLetterOrDigit can't tell what one is.
// Anything else is a Char check, which costs less, on every keystroke.

private fun String.isWordCharacterBefore(index: Int, textRules: TextRules): Boolean {
    val start = if (index >= 2 && isSurrogatePairAt(this, index - 2)) index - 2 else index - 1
    return isWordCharacterAt(start, textRules)
}

private fun String.isWordCharacterAt(index: Int, textRules: TextRules): Boolean =
    if (isSurrogatePairAt(this, index)) {
        textRules.isLetterOrDigit(codePointAt(this, index))
    } else {
        this[index].isLetterOrDigit()
    }
