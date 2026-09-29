package io.github.bryancassell.bluecard.ui

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.bryancassell.bluecard.R
import java.util.Locale

/**
 * The language of the app's strings (`strings_language`). It isn't always the device's: the app
 * may not have strings for the device's language.
 */
@Composable
fun stringsLocale(): Locale = Locale.forLanguageTag(stringResource(R.string.strings_language))

/**
 * Resources that format strings in [stringsLocale] rather than the device's language: numbers
 * use its digits and plurals its rules, so a sentence never mixes two languages. Use them for
 * every string with a number, such as "Do 2 of 3".
 */
// Lint warns that an app bundle may not install the resources for a locale set at runtime.
// This locale comes from the strings already shown, so their resources are installed.
@SuppressLint("AppBundleLocaleChanges")
@Composable
fun rememberStringsLanguageResources(): Resources {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = stringsLocale()
    return remember(context, configuration, locale) {
        val stringsConfiguration = Configuration(configuration).apply { setLocale(locale) }
        context.createConfigurationContext(stringsConfiguration).resources
    }
}
