package io.github.bryancassell.bluecard.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks BlueCardLightColorScheme and BlueCardDarkColorScheme against WCAG AA. Every text color
 * needs 4.5:1 on each background it can be drawn on, enough for body text and so also for large
 * text and icons. Outlines, which show where a text field is and which requirements aren't
 * complete yet, need 3:1.
 */
class BlueCardColorSchemeTest {
    private val schemes = mapOf(
        "light" to BlueCardLightColorScheme,
        "dark" to BlueCardDarkColorScheme
    )

    private fun ColorScheme.surfaces() = mapOf(
        "background" to background,
        "surface" to surface,
        "surfaceVariant" to surfaceVariant,
        "surfaceBright" to surfaceBright,
        "surfaceDim" to surfaceDim,
        "surfaceContainerLowest" to surfaceContainerLowest,
        "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest
    )

    // WCAG 2's contrast ratio.
    private fun contrast(a: Color, b: Color): Float {
        val lighter = maxOf(a.luminance(), b.luminance())
        val darker = minOf(a.luminance(), b.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun ColorScheme.onEverySurface(vararg colors: Pair<String, Color>) =
        colors.flatMap { (name, color) ->
            surfaces().map { (surfaceName, surface) ->
                Triple("$name on $surfaceName", color, surface)
            }
        }

    // Text in on<Role>Fixed or on<Role>FixedVariant can be drawn on <role>Fixed or <role>FixedDim.
    private fun fixedPairs(
        role: String,
        fixed: Color,
        fixedDim: Color,
        on: Color,
        onVariant: Color
    ) = listOf(
        "on${role}Fixed" to on,
        "on${role}FixedVariant" to onVariant
    ).flatMap { (name, text) ->
        listOf("Fixed" to fixed, "FixedDim" to fixedDim).map { (suffix, background) ->
            Triple("$name on ${role.lowercase()}$suffix", text, background)
        }
    }

    /** Checks the [pairs] each scheme gives, naming the scheme in each failure. */
    private fun assertAllMeet(
        minimum: Float,
        pairs: ColorScheme.() -> List<Triple<String, Color, Color>>
    ) {
        val failures = schemes.flatMap { (schemeName, scheme) ->
            scheme.pairs().mapNotNull { (name, foreground, background) ->
                val ratio = contrast(foreground, background)
                if (ratio < minimum) "$schemeName $name is %.2f:1".format(ratio) else null
            }
        }
        assertTrue("Below $minimum:1: $failures", failures.isEmpty())
    }

    @Test
    fun contrast_blackOnWhite_is21To1() {
        assertEquals(21f, contrast(Color.Black, Color.White), 0.01f)
    }

    @Test
    fun textColors_onEverySurface_meetBodyTextContrast() {
        assertAllMeet(4.5f) {
            onEverySurface(
                "onSurface" to onSurface,
                "onSurfaceVariant" to onSurfaceVariant,
                "primary" to primary,
                "secondary" to secondary,
                "tertiary" to tertiary,
                "error" to error
            )
        }
    }

    @Test
    fun onColors_onTheirColors_meetBodyTextContrast() {
        assertAllMeet(4.5f) {
            listOf(
                Triple("onBackground", onBackground, background),
                Triple("onPrimary", onPrimary, primary),
                // Home's rank card: its quieter text, and the ranks still to earn.
                Triple("inversePrimary on primary", inversePrimary, primary),
                Triple("onPrimaryContainer", onPrimaryContainer, primaryContainer),
                Triple("onSecondary", onSecondary, secondary),
                Triple("onSecondaryContainer", onSecondaryContainer, secondaryContainer),
                Triple("onTertiary", onTertiary, tertiary),
                Triple("onTertiaryContainer", onTertiaryContainer, tertiaryContainer),
                Triple("onError", onError, error),
                Triple("onErrorContainer", onErrorContainer, errorContainer),
                Triple("inverseOnSurface", inverseOnSurface, inverseSurface),
                Triple("inversePrimary on inverseSurface", inversePrimary, inverseSurface)
            ) +
                fixedPairs(
                    "Primary",
                    primaryFixed,
                    primaryFixedDim,
                    onPrimaryFixed,
                    onPrimaryFixedVariant
                ) +
                fixedPairs(
                    "Secondary",
                    secondaryFixed,
                    secondaryFixedDim,
                    onSecondaryFixed,
                    onSecondaryFixedVariant
                ) +
                fixedPairs(
                    "Tertiary",
                    tertiaryFixed,
                    tertiaryFixedDim,
                    onTertiaryFixed,
                    onTertiaryFixedVariant
                )
        }
    }

    @Test
    fun outline_onEverySurface_meetsNonTextContrast() {
        assertAllMeet(3f) { onEverySurface("outline" to outline) }
    }

    // The status card uses it to stand out from the page: white in light mode, rather than the
    // page's tint, and lighter than the page in dark mode.
    @Test
    fun surfaceBright_isTheBrightestSurface() {
        schemes.forEach { (schemeName, scheme) ->
            val brightest = scheme.surfaces().maxOf { it.value.luminance() }
            assertEquals(schemeName, brightest, scheme.surfaceBright.luminance())
            assertTrue(schemeName, scheme.surfaceBright.luminance() > scheme.background.luminance())
        }
    }
}
