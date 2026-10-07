package io.github.bryancassell.bluecard.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The app's light color scheme, used on every phone in light mode, so the app looks like the merit
 * badge blue card (see ARCHITECTURE.md, Theme).
 *
 * Material 3's standard light tones of the palettes that Material Color Utilities' fidelity
 * scheme makes from Scouting America Blue, #003F87, except:
 * - `primary` is Scouting America Blue itself.
 * - The background and surfaces are light tints of the card stock's Pale Blue, #9AB3D5. None
 *   is darker than tone 87, so every text color meets WCAG AA on all of them
 *   (BlueCardColorSchemeTest checks each pair).
 * - `surfaceBright` is white rather than `background`, so it's the brightest surface, as in the
 *   dark scheme. The status card on Badge detail and Rank detail uses it to stand out from the
 *   page.
 * - `error` is Material's default red. Scouting America Red, #CE1126, is below 4.5:1 on the
 *   darker surfaces.
 *
 * The window and splash screen show `background` before Compose draws, from
 * `R.color.background`, so the two must match.
 */
internal val BlueCardLightColorScheme = lightColorScheme(
    primary = Color(0xFF003F87),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7E2FF),
    onPrimaryContainer = Color(0xFF0D458D),
    inversePrimary = Color(0xFFACC7FF),
    secondary = Color(0xFF505E7D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E2FF),
    onSecondaryContainer = Color(0xFF384764),
    tertiary = Color(0xFF99461E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBCD),
    onTertiaryContainer = Color(0xFF7A2F07),
    background = Color(0xFFEAF1FE),
    onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFEAF1FE),
    onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFD4DAE7),
    onSurfaceVariant = Color(0xFF434751),
    surfaceTint = Color(0xFF003F87),
    inverseSurface = Color(0xFF2F3035),
    inverseOnSurface = Color(0xFFF0F0F7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF737782),
    outlineVariant = Color(0xFFC3C6D2),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFDFE6F3),
    surfaceContainerHigh = Color(0xFFD9E0ED),
    surfaceContainerHighest = Color(0xFFD4DAE7),
    surfaceContainerLow = Color(0xFFE5EBF8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD4DAE7),
    primaryFixed = Color(0xFFD7E2FF),
    primaryFixedDim = Color(0xFFACC7FF),
    onPrimaryFixed = Color(0xFF001A40),
    onPrimaryFixedVariant = Color(0xFF0D458D),
    secondaryFixed = Color(0xFFD7E2FF),
    secondaryFixedDim = Color(0xFFB8C7E9),
    onSecondaryFixed = Color(0xFF0B1B36),
    onSecondaryFixedVariant = Color(0xFF384764),
    tertiaryFixed = Color(0xFFFFDBCD),
    tertiaryFixedDim = Color(0xFFFFB597),
    onTertiaryFixed = Color(0xFF360F00),
    onTertiaryFixedVariant = Color(0xFF7A2F07)
)

/**
 * The app's dark color scheme, used on every phone in dark mode: the blue card's colors on Dark
 * Blue (see ARCHITECTURE.md, Theme).
 *
 * Material 3's standard dark tones of the same palettes as [BlueCardLightColorScheme], except:
 * - `primary` is the card stock's Pale Blue, #9AB3D5.
 * - `inversePrimary` is Scouting America Blue, the light scheme's `primary`. Material's tone 40
 *   is below 4.5:1 on Pale Blue, where Home's rank card draws its quieter text.
 * - `onPrimary` is tone 10 rather than 20, so the rank card's text stands apart from that
 *   quieter text as much as in light mode.
 * - The background and surfaces are Scouting America Dark Blue, #003366, and darker shades of
 *   it. Dark Blue is the highest, so every text color meets WCAG AA on all of them
 *   (BlueCardColorSchemeTest checks each pair).
 *
 * `background` is also `R.color.background` in `values-night`, for the window and splash screen.
 */
internal val BlueCardDarkColorScheme = darkColorScheme(
    primary = Color(0xFF9AB3D5),
    onPrimary = Color(0xFF001A40),
    primaryContainer = Color(0xFF0D458D),
    onPrimaryContainer = Color(0xFFD7E2FF),
    inversePrimary = Color(0xFF003F87),
    secondary = Color(0xFFB8C7E9),
    onSecondary = Color(0xFF22304C),
    secondaryContainer = Color(0xFF384764),
    onSecondaryContainer = Color(0xFFD7E2FF),
    tertiary = Color(0xFFFFB597),
    onTertiary = Color(0xFF581D00),
    tertiaryContainer = Color(0xFF7A2F07),
    onTertiaryContainer = Color(0xFFFFDBCD),
    background = Color(0xFF00132D),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF00132D),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF003366),
    onSurfaceVariant = Color(0xFFC3C6D2),
    surfaceTint = Color(0xFF9AB3D5),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2F3035),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8D909C),
    outlineVariant = Color(0xFF434751),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF003366),
    surfaceContainer = Color(0xFF001F43),
    surfaceContainerHigh = Color(0xFF002A55),
    surfaceContainerHighest = Color(0xFF003366),
    surfaceContainerLow = Color(0xFF001B3C),
    surfaceContainerLowest = Color(0xFF000E24),
    surfaceDim = Color(0xFF00132D),
    primaryFixed = Color(0xFFD7E2FF),
    primaryFixedDim = Color(0xFFACC7FF),
    onPrimaryFixed = Color(0xFF001A40),
    onPrimaryFixedVariant = Color(0xFF0D458D),
    secondaryFixed = Color(0xFFD7E2FF),
    secondaryFixedDim = Color(0xFFB8C7E9),
    onSecondaryFixed = Color(0xFF0B1B36),
    onSecondaryFixedVariant = Color(0xFF384764),
    tertiaryFixed = Color(0xFFFFDBCD),
    tertiaryFixedDim = Color(0xFFFFB597),
    onTertiaryFixed = Color(0xFF360F00),
    onTertiaryFixedVariant = Color(0xFF7A2F07)
)
