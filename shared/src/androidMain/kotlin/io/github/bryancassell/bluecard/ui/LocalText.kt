package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.bryancassell.bluecard.text.LocaleFormats
import io.github.bryancassell.bluecard.text.TextRules

// The platform's text formats and rules, for the screens. Their implementations are the
// platform's, so neither has a default here: content shown without them, as in a test, fails at
// once rather than formatting some other way.

/**
 * Writes dates, numbers, lists and typed text in the strings' locale.
 * `ProvideStringsLanguageResources` provides it.
 */
val LocalLocaleFormats = staticCompositionLocalOf<LocaleFormats> {
    error("LocalLocaleFormats isn't provided: ProvideStringsLanguageResources provides it")
}

/** How text sorts and where it can be broken. [BlueCardApp] provides it. */
val LocalTextRules = staticCompositionLocalOf<TextRules> {
    error("LocalTextRules isn't provided: BlueCardApp provides it")
}
