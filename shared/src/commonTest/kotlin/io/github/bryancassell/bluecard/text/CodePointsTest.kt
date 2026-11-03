package io.github.bryancassell.bluecard.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
}
