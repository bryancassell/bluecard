package io.github.bryancassell.bluecard.di

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogMessagesTest {
    @Test
    fun shortText_isOneMessage() {
        assertEquals(listOf("Tag: it failed"), logMessages("Tag: it failed"))
    }

    @Test
    fun longText_isSplitAtTheEndsOfLines() {
        val text = (1..60).joinToString("\n") { "    at io.github.bryancassell.Frame$it(Source)" }

        val messages = logMessages(text)

        assertTrue(messages.size > 1)
        assertTrue(messages.all { it.encodeToByteArray().size <= 1000 }, "$messages")
        assertEquals(text, messages.joinToString("\n"))
        // So every frame is on one line of one message.
        assertTrue(messages.flatMap { it.lines() }.all { it in text.lines() })
    }

    @Test
    fun lineTooLongForOneMessage_isCut() {
        val line = "x".repeat(25)

        assertEquals(listOf("x".repeat(10), "x".repeat(10), "x".repeat(5)), logMessages(line, 10))
    }

    // A grinning face is two UTF-16 units and four bytes of UTF-8.
    @Test
    fun lineTooLongForOneMessage_isCutBetweenCharacters() {
        val emoji = charArrayOf(0xD83D.toChar(), 0xDE00.toChar()).concatToString()
        val line = "ab" + emoji.repeat(3)

        val messages = logMessages(line, 6)

        assertEquals(listOf("ab$emoji", emoji, emoji), messages)
    }

    @Test
    fun noText_isOneEmptyMessage() {
        assertEquals(listOf(""), logMessages(""))
    }
}
