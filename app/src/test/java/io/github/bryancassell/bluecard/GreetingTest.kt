package io.github.bryancassell.bluecard

import org.junit.Assert.assertEquals
import org.junit.Test

/** Local unit test: runs on your computer's JVM, no device needed. */
class GreetingTest {
    @Test
    fun greetingText_includesName() {
        assertEquals("Welcome to BlueCard, Scout!", greetingText("Scout"))
    }
}
