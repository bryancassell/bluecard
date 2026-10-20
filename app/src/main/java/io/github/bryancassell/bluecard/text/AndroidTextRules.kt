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

    override fun characterBoundaryAtOrBefore(text: String, offset: Int): Int =
        characters(text).run { if (isBoundary(offset)) offset else preceding(offset) }

    override fun characterBoundaryAtOrAfter(text: String, offset: Int): Int =
        characters(text).run { if (isBoundary(offset)) offset else following(offset) }

    override fun isLineBreak(text: String, offset: Int): Boolean =
        BreakIterator.getLineInstance().apply { setText(text) }.isBoundary(offset)

    // ICU works out boundaries near the offset it's asked about, not across the whole text.
    private fun characters(text: String): BreakIterator =
        BreakIterator.getCharacterInstance().apply { setText(text) }
}
