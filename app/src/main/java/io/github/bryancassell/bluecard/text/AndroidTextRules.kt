package io.github.bryancassell.bluecard.text

import android.icu.text.BreakIterator
import java.text.Collator
import java.util.Locale

/** [TextRules] with Java's collator and Android's ICU break iterators. */
class AndroidTextRules : TextRules {
    // Android's Collator is built on ICU's, which "is thread-safe only when frozen", so each sort
    // gets its own.
    override fun englishOrder(): Comparator<String> =
        Comparator(Collator.getInstance(Locale.ENGLISH)::compare)

    override fun characterBoundaries(text: String): List<Int> =
        BreakIterator.getCharacterInstance().boundaries(text)

    override fun lineBreaks(text: String): List<Int> =
        BreakIterator.getLineInstance().boundaries(text)

    private fun BreakIterator.boundaries(text: String): List<Int> {
        setText(text)
        return generateSequence(first()) { next().takeIf { it != BreakIterator.DONE } }.toList()
    }
}
