package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.Composable
import io.github.bryancassell.bluecard.text.LocaleFormats

/**
 * Writes dates, numbers, lists and typed text in the locale of the strings' language
 * (`strings_language`), for [LocalLocaleFormats], so a sentence never mixes two languages.
 */
@Composable
expect fun rememberLocaleFormats(): LocaleFormats
