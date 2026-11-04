package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.strings_language
import io.github.bryancassell.bluecard.text.IosLocaleFormats
import io.github.bryancassell.bluecard.text.LocaleFormats
import io.github.bryancassell.bluecard.text.currentRegion
import io.github.bryancassell.bluecard.text.deviceRegions
import io.github.bryancassell.bluecard.text.stringsLocale
import org.jetbrains.compose.resources.stringResource
import platform.Foundation.NSLocale

/** Writes in the strings' locale ([rememberStringsLocale]). */
@Composable
actual fun rememberLocaleFormats(): LocaleFormats {
    val locale = rememberStringsLocale()
    return remember(locale) { IosLocaleFormats(locale) }
}

/** The strings' locale ([stringsLocale]), in the device's region as it changes. */
@Composable
internal fun rememberStringsLocale(): NSLocale {
    val stringsLanguage = stringResource(Res.string.strings_language)
    val initialRegion = remember { currentRegion() }
    val region by remember { deviceRegions() }.collectAsState(initialRegion)
    return remember(stringsLanguage, region) { stringsLocale(stringsLanguage, region) }
}
