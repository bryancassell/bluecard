package io.github.bryancassell.bluecard.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The app's only color scheme, used on every phone in light and dark mode, so the app looks like
 * the merit badge blue card (see ARCHITECTURE.md, UI layer).
 *
 * Material 3's standard light tones of the palettes that Material Color Utilities' fidelity
 * scheme makes from Scouting America Blue, #003F87, except:
 * - `primary` is Scouting America Blue itself.
 * - The background and surfaces are light tints of the card stock's Pale Blue, #9AB3D5. None
 *   is darker than tone 87, so every text color meets WCAG AA on all of them
 *   (BlueCardColorSchemeTest checks each pair).
 * - `error` is Material's default red. Scouting America Red, #CE1126, is below 4.5:1 on the
 *   darker surfaces.
 *
 * The window and splash screen show `background` before Compose draws, from
 * `R.color.background`, so the two must match.
 */
internal val BlueCardColorScheme = lightColorScheme(
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
    surfaceBright = Color(0xFFEAF1FE),
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
