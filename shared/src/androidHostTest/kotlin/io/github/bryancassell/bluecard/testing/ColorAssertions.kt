package io.github.bryancassell.bluecard.testing

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals

/**
 * Asserts that a pixel [shown] is the color [expected], to one step of 8-bit color either way,
 * from rounding. Alpha isn't compared, since a captured pixel is opaque.
 */
fun assertColor(expected: Color, shown: Color) {
    val step = 1.5f / 255
    listOf(
        expected.red to shown.red,
        expected.green to shown.green,
        expected.blue to shown.blue
    ).forEach { (want, got) -> assertEquals("$expected, shown $shown", want, got, step) }
}
