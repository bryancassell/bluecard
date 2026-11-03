package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.LayoutDirection
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleLanguageDirectionRightToLeft
import platform.Foundation.characterDirectionForLanguage

/**
 * The direction of Compose's current locale, the first preferred language, which Material 3 reads
 * on iOS. The app makes it the strings' language (`useStringsLanguageByDefault`).
 */
@Composable
@ReadOnlyComposable
internal actual fun pickerLayoutDirection(): LayoutDirection =
    layoutDirectionOf(Locale.current.toLanguageTag())

/**
 * The direction [language], a BCP 47 tag such as "fa", is written in. Its script counts: "pa" is
 * left-to-right, but "pa-Arab" right-to-left.
 */
internal fun layoutDirectionOf(language: String): LayoutDirection =
    when (NSLocale.characterDirectionForLanguage(language)) {
        NSLocaleLanguageDirectionRightToLeft -> LayoutDirection.Rtl
        else -> LayoutDirection.Ltr
    }
