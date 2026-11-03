package io.github.bryancassell.bluecard.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CodePointsTest {
    // "a", then U+20000, a CJK letter written as two surrogates, then "b".
    private val text = "a" + Char(0xD840) + Char(0xDC00) + "b"

    @Test
    fun codePoints_readASurrogatePairAsOne() {
        assertEquals(listOf('a'.code, 0x20000, 'b'.code), codePoints(text).toList())
    }

    @Test
    fun codePointAt_aPairsFirstHalf_isThePairs() {
        assertEquals(0x20000, codePointAt(text, 1))
        assertEquals('b'.code, codePointAt(text, 3))
    }

    @Test
    fun isSurrogatePairAt_isTrueOnlyWhereAPairStarts() {
        assertFalse(isSurrogatePairAt(text, 0))
        assertTrue(isSurrogatePairAt(text, 1))
        assertFalse(isSurrogatePairAt(text, 2))
    }

    // As half an emoji left by a paste is.
    @Test
    fun aLoneSurrogate_isItsOwnCodePoint() {
        val lone = "a" + Char(0xD840)

        assertFalse(isSurrogatePairAt(lone, 1))
        assertEquals(listOf('a'.code, 0xD840), codePoints(lone).toList())
    }

    // 🐢 is high then low. A low surrogate first, a high one before a whole 🐢, a low one right
    // after it, and a high one last.
    private val high = Char(0xD83D)
    private val low = Char(0xDC22)
    private val mixed = "$low $high$high$low$low$high"

    @Test
    fun isLoneSurrogateAt_isTrueOnlyForASurrogateOutsideAPair() {
        assertEquals(listOf(0, 2, 5, 6), mixed.indices.filter { isLoneSurrogateAt(mixed, it) })
    }

    @Test
    fun replacingLoneSurrogates_replacesEachWithTheReplacementCharacter_andKeepsWholeEmoji() {
        val r = Char(0xFFFD)

        assertEquals("$r $r$high$low$r$r", replacingLoneSurrogates(mixed))
    }

    @Test
    fun replacingLoneSurrogates_withAReplacement_usesItsTextForEach() {
        val replaced = replacingLoneSurrogates("a$high$low b$low") { "<${it.code.toString(16)}>" }

        assertEquals("a$high$low b<dc22>", replaced)
    }

    @Test
    fun replacingLoneSurrogates_withoutALoneSurrogate_returnsTheTextItself() {
        val text = "Lake $high$low"

        assertSame(text, replacingLoneSurrogates(text))
    }
}
