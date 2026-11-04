package io.github.bryancassell.bluecard.text

// Code points, which Kotlin's common library doesn't read: a String is UTF-16 units, and a code
// point beyond the Basic Multilingual Plane, such as U+20000, a CJK letter, is two of them, a
// surrogate pair. Functions of the text rather than extensions, so Java's own String.codePointAt
// and codePoints() don't hide them on Android.

/** Whether a surrogate pair starts at [index] of [text]. */
internal fun isSurrogatePairAt(text: String, index: Int): Boolean =
    text[index].isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()

/**
 * Whether a lone surrogate is at [index] of [text]: one that isn't half of a pair, such as half
 * an emoji left by a paste.
 */
internal fun isLoneSurrogateAt(text: String, index: Int): Boolean {
    if (!text[index].isSurrogate()) return false
    val pairEndsHere = index > 0 && isSurrogatePairAt(text, index - 1)
    return !isSurrogatePairAt(text, index) && !pairEndsHere
}

/**
 * [text] with each lone surrogate replaced with [replacement]'s text for it, by default U+FFFD,
 * the replacement character. The app stores what the scout types that way (`normalizedText`,
 * `normalizedTrackerValues`, `Profile.normalized`). A lone surrogate isn't a character, and
 * storing one loses more than it. As androidx.sqlite 2.7.1 bundles it, SQLite joins one with the
 * character after it, which is lost (#390). Java's UTF-8 encoder, which an export and DataStore
 * use on Android, writes `?` for it (checked on the JVM). Replaced, the text reads back, exports
 * and imports as it's shown. U+FFFD is one UTF-16 unit, as the surrogate was, so the text still
 * fits its field's length limit.
 */
internal fun replacingLoneSurrogates(
    text: String,
    replacement: (Char) -> String = { "\uFFFD" }
): String {
    val first = text.indices.firstOrNull { isLoneSurrogateAt(text, it) } ?: return text
    return buildString(text.length) {
        appendRange(text, 0, first)
        for (i in first until text.length) {
            if (isLoneSurrogateAt(text, i)) append(replacement(text[i])) else append(text[i])
        }
    }
}

/**
 * The code point at [index] of [text]: the surrogate pair's, if one starts there, or else the
 * UTF-16 unit's own, as for a lone surrogate.
 */
internal fun codePointAt(text: String, index: Int): Int = if (isSurrogatePairAt(text, index)) {
    0x10000 + ((text[index].code - 0xD800) shl 10) + (text[index + 1].code - 0xDC00)
} else {
    text[index].code
}

/** [text]'s code points, in order. */
internal fun codePoints(text: String): Sequence<Int> = sequence {
    var index = 0
    while (index < text.length) {
        yield(codePointAt(text, index))
        index += if (isSurrogatePairAt(text, index)) 2 else 1
    }
}
