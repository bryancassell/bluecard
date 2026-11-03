@file:OptIn(ExperimentalForeignApi::class)

package io.github.bryancassell.bluecard.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringGetCharacterAtIndex
import platform.CoreFoundation.CFStringGetLength

class CFStringsTest {
    @Test
    fun toCFString_hasTheSameCodeUnits() {
        // An accent that combines, and an emoji, which is a surrogate pair.
        val text = "Cafe\u0301 \uD83C\uDFD5\uFE0F camp"

        val string = text.toCFString()
        try {
            assertEquals(text.length.toLong(), CFStringGetLength(string))
            for (index in text.indices) {
                val unit = CFStringGetCharacterAtIndex(string, index.toLong())
                assertEquals(text[index].code.toUShort(), unit)
            }
        } finally {
            CFRelease(string)
        }
    }
}
