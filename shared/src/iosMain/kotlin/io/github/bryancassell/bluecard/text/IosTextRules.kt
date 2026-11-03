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
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSLocale
import platform.Foundation.NSMakeRange
import platform.Foundation.NSString
import platform.Foundation.compare
import platform.Foundation.rangeOfComposedCharacterSequenceAtIndex

/**
 * [TextRules] on iOS. Characters are Foundation's composed character sequences, which keep an
 * emoji or a letter and its accent mark whole, and line breaks are Core Foundation's, which follow
 * Unicode's line breaking rules as Android's ICU does. Sorting is Foundation's comparison in
 * English, which follows the English collation rules, as Android's collator does, and letters and
 * digits are Foundation's character sets.
 */
@OptIn(ExperimentalForeignApi::class)
class IosTextRules : TextRules {
    @Suppress("CAST_NEVER_SUCCEEDS") // A Kotlin string is an NSString on iOS.
    override fun englishOrder(): Comparator<String> {
        val english = NSLocale("en")
        return Comparator { a, b ->
            (a as NSString).compare(b, 0u, NSMakeRange(0u, a.length.toULong()), english).toInt()
        }
    }

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

    // Foundation's decimal digits are Unicode's category Nd, as Java's digits are.
    override fun isLetterOrDigit(codePoint: Int): Boolean = isLetter(codePoint) ||
        NSCharacterSet.decimalDigitCharacterSet.longCharacterIsMember(codePoint.toUInt())

    // The character that starts at or holds the code unit at [offset], or null at the end.
    @Suppress("CAST_NEVER_SUCCEEDS") // A Kotlin string is an NSString on iOS.
    private fun characterAt(text: String, offset: Int): IntRange? {
        if (offset == text.length) return null
        return (text as NSString).rangeOfComposedCharacterSequenceAtIndex(offset.toULong())
            .useContents { location.toInt() until (location + length).toInt() }
    }
}

/**
 * Whether [codePoint] is a letter, Unicode's category L, as Java's `Character.isLetter` tells.
 * Foundation's letters are L and the marks M, so the marks are taken out.
 */
internal fun isLetter(codePoint: Int): Boolean {
    val char = codePoint.toUInt()
    return NSCharacterSet.letterCharacterSet.longCharacterIsMember(char) &&
        !NSCharacterSet.nonBaseCharacterSet.longCharacterIsMember(char)
}
