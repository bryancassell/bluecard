package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

class PickerLayoutDirectionTest {
    @Test
    fun rightToLeftLanguages_areRtl() {
        for (language in listOf("fa", "ar", "he", "ur")) {
            assertEquals(LayoutDirection.Rtl, layoutDirectionOf(language), language)
        }
    }

    @Test
    fun leftToRightLanguages_areLtr() {
        for (language in listOf("en", "fr", "zh", "hi")) {
            assertEquals(LayoutDirection.Ltr, layoutDirectionOf(language), language)
        }
    }

    // Punjabi is written left-to-right, but right-to-left in its Arabic script.
    @Test
    fun aLanguagesScript_decidesItsDirection() {
        assertEquals(LayoutDirection.Ltr, layoutDirectionOf("pa"))
        assertEquals(LayoutDirection.Rtl, layoutDirectionOf("pa-Arab"))
    }
}
