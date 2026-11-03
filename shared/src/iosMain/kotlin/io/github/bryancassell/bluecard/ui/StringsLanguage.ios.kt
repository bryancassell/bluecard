package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.Composable
import io.github.bryancassell.bluecard.text.EnglishLocaleFormats
import io.github.bryancassell.bluecard.text.LocaleFormats

/** Writes in US English, the strings' only language on iOS until #350. */
@Composable
actual fun rememberLocaleFormats(): LocaleFormats = EnglishLocaleFormats
