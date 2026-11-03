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
    val pairStartsHere = isSurrogatePairAt(text, index)
    val pairEndsHere = index > 0 && isSurrogatePairAt(text, index - 1)
    return text[index].isSurrogate() && !pairStartsHere && !pairEndsHere
}

/**
 * [text] with each lone surrogate replaced with U+FFFD, the replacement character, as the app
 * stores what the scout types (`normalizedText`, `normalizedTrackerValues` and the profile's
 * pages). A lone surrogate isn't a character, and storing one loses more than it. As
 * androidx.sqlite 2.7.1 bundles it, SQLite joins one with the character after it, which is lost
 * (#390). Java's UTF-8 encoder, which an export and DataStore use on Android, writes `?` for it
 * (checked on the JVM). Replaced, the text reads back, exports and imports as it's shown. Each
 * UTF-16 unit stays one, so the text still fits its field's length limit.
 */
internal fun replacingLoneSurrogates(text: String): String {
    if (text.indices.none { isLoneSurrogateAt(text, it) }) return text
    return CharArray(text.length) { if (isLoneSurrogateAt(text, it)) '\uFFFD' else text[it] }
        .concatToString()
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
