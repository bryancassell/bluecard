package io.github.bryancassell.bluecard.text

// Code points, which Kotlin's common library doesn't read: a String is UTF-16 units, and a code
// point beyond the Basic Multilingual Plane, such as U+20000, a CJK letter, is two of them, a
// surrogate pair. Functions of the text rather than extensions, so Java's own String.codePointAt
// and codePoints() don't hide them on Android.

/** Whether a surrogate pair starts at [index] of [text]. */
internal fun isSurrogatePairAt(text: String, index: Int): Boolean =
    text[index].isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()

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
