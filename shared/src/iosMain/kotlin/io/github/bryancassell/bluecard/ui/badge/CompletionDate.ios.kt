package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.LayoutDirection
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleLanguageDirectionRightToLeft
import platform.Foundation.characterDirectionForLanguage

/** The direction of the device's first language, Compose's current locale. #350 checks it. */
@Composable
@ReadOnlyComposable
internal actual fun pickerLayoutDirection(): LayoutDirection =
    layoutDirectionOf(Locale.current.language)

/** The direction [language], an ISO 639 code such as "fa", is written in. */
internal fun layoutDirectionOf(language: String): LayoutDirection =
    when (NSLocale.characterDirectionForLanguage(language)) {
        NSLocaleLanguageDirectionRightToLeft -> LayoutDirection.Rtl
        else -> LayoutDirection.Ltr
    }
