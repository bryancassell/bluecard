package io.github.bryancassell.bluecard.text

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatOnlyPunctuationTest {
    // As the Kotlin port of libphonenumber wrote it outside its libphonenumberCompat mode. The
    // digits typed apart stay apart, so only the words give it away.
    @Test
    fun formattedWithWordsAdded_isAsTyped() {
        assertEquals(
            "214-555-0142-12",
            formatOnlyPunctuation("214-555-0142-12") { "(214) 555-0142 ext. 12" }
        )
    }

    @Test
    fun formattedWithOnlyPunctuationChanged_isFormatted() {
        assertEquals("(214) 555-0142", formatOnlyPunctuation("2145550142") { "(214) 555-0142" })
    }
}
