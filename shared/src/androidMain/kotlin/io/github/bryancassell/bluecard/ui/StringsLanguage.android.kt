package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.strings_language
import io.github.bryancassell.bluecard.text.AndroidLocaleFormats
import io.github.bryancassell.bluecard.text.LocaleFormats
import io.github.bryancassell.bluecard.text.stringsLocales
import java.util.Locale
import org.jetbrains.compose.resources.stringResource

/**
 * The locale the app's strings are formatted in, from [stringsLocales]. It's in the language
 * of the strings (`strings_language`), which isn't always the device's: the app may not have
 * strings for the device's language. Code outside Compose uses `text.stringsLocale(context)`.
 */
@Composable
fun stringsLocale(): Locale {
    val stringsLanguage = stringResource(Res.string.strings_language)
    val device = LocalConfiguration.current.locales
    return remember(device, stringsLanguage) {
        stringsLocales(device, Locale.forLanguageTag(stringsLanguage))[0]
    }
}

/** Writes in [stringsLocale]. */
@Composable
actual fun rememberLocaleFormats(): LocaleFormats {
    val locale = stringsLocale()
    return remember(locale) { AndroidLocaleFormats(locale) }
}
