package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.LayoutDirection
import io.github.bryancassell.bluecard.text.isRightToLeft

/**
 * The direction of Compose's current locale, the first preferred language, which Material 3 reads
 * on iOS. The app makes it the strings' locale (`useStringsLanguageByDefault`).
 */
@Composable
@ReadOnlyComposable
internal actual fun pickerLayoutDirection(): LayoutDirection =
    layoutDirectionOf(Locale.current.toLanguageTag())

/** The direction [language], a BCP 47 tag such as "fa" or "pa-Arab", is written in. */
internal fun layoutDirectionOf(language: String): LayoutDirection =
    if (isRightToLeft(language)) LayoutDirection.Rtl else LayoutDirection.Ltr
