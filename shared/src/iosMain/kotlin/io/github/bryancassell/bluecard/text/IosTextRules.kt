package io.github.bryancassell.bluecard.text

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSString
import platform.Foundation.rangeOfComposedCharacterSequenceAtIndex

/**
 * [TextRules] on iOS. Characters are Foundation's composed character sequences, which keep an
 * emoji or a letter and its accent mark whole. Sorting and line breaks are plain-Kotlin stand-ins
 * for English text until #350.
 */
class IosTextRules : TextRules {
    // Ignores case and then breaks ties by it, as Android's collator does. Unlike it, an accented
    // letter sorts after the base letters, which the catalog's English names don't have.
    override fun englishOrder(): Comparator<String> =
        compareBy<String> { it.lowercase() }.thenBy { it }

    override fun characterBoundaryAtOrBefore(text: String, offset: Int): Int =
        characterAt(text, offset)?.first ?: offset

    override fun characterBoundaryAtOrAfter(text: String, offset: Int): Int =
        characterAt(text, offset)?.let { if (it.first == offset) offset else it.last + 1 }
            ?: offset

    // A line can break after a space or a hyphen, before the next word.
    override fun isLineBreak(text: String, offset: Int): Boolean =
        offset == 0 || offset == text.length ||
            (
                (text[offset - 1].isWhitespace() || text[offset - 1] == '-') &&
                    !text[offset].isWhitespace()
                )

    // The character that starts at or holds the code unit at [offset], or null at the end.
    @OptIn(ExperimentalForeignApi::class)
    @Suppress("CAST_NEVER_SUCCEEDS") // A Kotlin string is an NSString on iOS.
    private fun characterAt(text: String, offset: Int): IntRange? {
        if (offset == text.length) return null
        return (text as NSString).rangeOfComposedCharacterSequenceAtIndex(offset.toULong())
            .useContents { location.toInt() until (location + length).toInt() }
    }
}
