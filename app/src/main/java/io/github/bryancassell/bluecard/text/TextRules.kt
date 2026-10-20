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
     * [offset] if it's between two characters of [text] as a reader sees them (grapheme
     * clusters), or else the nearest such boundary before it, so a cut there never splits an
     * emoji made of several code points or a letter from its accent mark. The start and end of
     * the text are boundaries. Asked about one offset rather than the whole text, so a huge paste
     * costs little more than a short one. [offset] is from 0 to the text's length, as for
     * [characterBoundaryAtOrAfter] and [isLineBreak].
     */
    fun characterBoundaryAtOrBefore(text: String, offset: Int): Int

    /** As [characterBoundaryAtOrBefore], but the nearest boundary after [offset]. */
    fun characterBoundaryAtOrAfter(text: String, offset: Int): Int

    /**
     * Whether a line of [text] may break at [offset], such as after a space. A line that ends
     * anywhere else breaks a word. The start and end of the text are line breaks.
     */
    fun isLineBreak(text: String, offset: Int): Boolean
}
