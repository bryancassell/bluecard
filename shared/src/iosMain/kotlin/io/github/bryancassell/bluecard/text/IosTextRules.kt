package io.github.bryancassell.bluecard.text

import cnames.structs.__CFString
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import platform.CoreFoundation.CFRangeMake
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringTokenizerCreate
import platform.CoreFoundation.CFStringTokenizerGetCurrentTokenRange
import platform.CoreFoundation.CFStringTokenizerGoToTokenAtIndex
import platform.CoreFoundation.kCFStringTokenizerUnitLineBreak
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSString
import platform.Foundation.rangeOfComposedCharacterSequenceAtIndex

/**
 * [TextRules] on iOS. Characters are Foundation's composed character sequences, which keep an
 * emoji or a letter and its accent mark whole, and line breaks are Core Foundation's, which follow
 * Unicode's line breaking rules as Android's ICU does. Sorting is a plain-Kotlin stand-in for
 * English names until #350.
 */
@OptIn(ExperimentalForeignApi::class)
class IosTextRules : TextRules {
    // Ignores case and then breaks ties by it, lowercase first, as Android's collator does. Unlike
    // it, an accented letter sorts after the base letters, which the catalog's English names don't
    // have.
    override fun englishOrder(): Comparator<String> =
        String.CASE_INSENSITIVE_ORDER.thenByDescending { it }

    override fun characterBoundaryAtOrBefore(text: String, offset: Int): Int =
        characterAt(text, offset)?.first ?: offset

    override fun characterBoundaryAtOrAfter(text: String, offset: Int): Int =
        characterAt(text, offset)?.let { if (it.first == offset) offset else it.last + 1 }
            ?: offset

    // Each line-break token runs from one place a line may break to the next, so [offset] is one
    // where the token that holds it starts.
    @Suppress("CAST_NEVER_SUCCEEDS") // A Kotlin string is an NSString on iOS.
    override fun isLineBreak(text: String, offset: Int): Boolean {
        if (offset == 0 || offset == text.length) return true
        val string = CFBridgingRetain(text as NSString)!!.reinterpret<__CFString>()
        val tokenizer = CFStringTokenizerCreate(
            null,
            string,
            CFRangeMake(0, text.length.toLong()),
            kCFStringTokenizerUnitLineBreak,
            null
        )
        try {
            CFStringTokenizerGoToTokenAtIndex(tokenizer, offset.toLong())
            return CFStringTokenizerGetCurrentTokenRange(tokenizer)
                .useContents { location == offset.toLong() }
        } finally {
            CFRelease(tokenizer)
            CFRelease(string)
        }
    }

    // The character that starts at or holds the code unit at [offset], or null at the end.
    @Suppress("CAST_NEVER_SUCCEEDS") // A Kotlin string is an NSString on iOS.
    private fun characterAt(text: String, offset: Int): IntRange? {
        if (offset == text.length) return null
        return (text as NSString).rangeOfComposedCharacterSequenceAtIndex(offset.toULong())
            .useContents { location.toInt() until (location + length).toInt() }
    }
}
