package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.Composable
import io.github.bryancassell.bluecard.text.LocaleFormats

/**
 * Writes dates, numbers, lists and typed text in the strings' locale, for [LocalLocaleFormats],
 * so a sentence never mixes two languages. That locale is in the language of the strings
 * (`strings_language`), which isn't always the device's: the app may not have strings for the
 * device's language.
 */
@Composable
expect fun rememberLocaleFormats(): LocaleFormats
