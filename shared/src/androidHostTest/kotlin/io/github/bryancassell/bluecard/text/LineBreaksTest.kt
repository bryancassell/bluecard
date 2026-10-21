package io.github.bryancassell.bluecard.text

import org.junit.Assert.assertEquals
import org.junit.Test

class LineBreaksTest {
    @Test
    fun lineBreaksAsSpaces_replacesEachLineBreakWithASpace() {
        val text = "a\n\nb\r\n\r\nc\r\rd\n\re\u000Bf\u000Cg\u0085h\u2028i\u2029j"

        assertEquals("a  b  c  d  e f g h i j", lineBreaksAsSpaces(text))
    }

    @Test
    fun lineBreaksAsSpaces_keepsOtherSpacing() {
        assertEquals(" a\tb  c ", lineBreaksAsSpaces(" a\tb  c "))
    }
}
