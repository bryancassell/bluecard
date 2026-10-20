package io.github.bryancassell.bluecard.text

/**
 * How text sorts and where it can be broken, by Unicode's rules, which come from the platform:
 * Kotlin's common libraries don't have them (#341). Unlike [LocaleFormats], they don't depend on
 * the strings' language. Behavior every implementation must have is in `TextRulesContract`.
 */
interface TextRules {
    /**
     * The order English text is listed in, such as badge names: an accented letter sorts with its
     * base letter, and case only breaks ties. Get a new one for each sort, as one may not be safe
     * to use on two threads at once.
     */
    fun englishOrder(): Comparator<String>

    /**
     * The offsets in [text] between characters as a reader sees them (grapheme clusters), from 0
     * to its length, so an emoji made of several code points or a letter with an accent mark is
     * one character.
     */
    fun characterBoundaries(text: String): List<Int>

    /**
     * The offsets in [text] where a line may break, such as after a space, from 0 to its length.
     * A line that ends anywhere else breaks a word.
     */
    fun lineBreaks(text: String): List<Int>
}
