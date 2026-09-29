package io.github.bryancassell.bluecard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Configuration.SCREENLAYOUT_LAYOUTDIR_MASK
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import io.github.bryancassell.bluecard.R
import java.util.Locale

/**
 * The locale the app's strings are formatted in, from [stringsLocales]. It's in the language
 * of the strings (`strings_language`), which isn't always the device's: the app may not have
 * strings for the device's language.
 */
@Composable
@ReadOnlyComposable
fun stringsLocale(): Locale =
    stringsLocales(LocalConfiguration.current.locales, stringsLanguage())[0]

/** [stringsLocale] for code outside Compose, such as an activity before it has content. */
fun stringsLocale(context: Context): Locale = stringsLocales(
    context.resources.configuration.locales,
    Locale.forLanguageTag(context.getString(R.string.strings_language))
)[0]

/**
 * Provides [LocalResources] in the strings' language to [content], so every `stringResource`
 * and `pluralStringResource` formats numbers with its digits and picks plural forms by its
 * rules, and a sentence never mixes two languages.
 */
// Lint warns that an app bundle may not install the resources for a locale set at runtime.
// These are the language of the strings already shown and the device's own locales, so their
// resources are installed.
@SuppressLint("AppBundleLocaleChanges")
@Composable
fun ProvideStringsLanguageResources(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locales = stringsLocales(configuration.locales, stringsLanguage())
    // When the device's own locales are already right, as on an English phone, its resources
    // are used as they are.
    val resources = if (locales == configuration.locales) {
        null
    } else {
        remember(context, configuration, locales) {
            val stringsConfiguration = Configuration(configuration).apply {
                setLocales(locales)
                // setLocales also sets the layout direction from the first locale. Keep the
                // configuration's, which the layout follows: MainActivity sets it from the
                // strings' language.
                screenLayout = screenLayout and SCREENLAYOUT_LAYOUTDIR_MASK.inv() or
                    (configuration.screenLayout and SCREENLAYOUT_LAYOUTDIR_MASK)
            }
            context.createConfigurationContext(stringsConfiguration).resources
        }
    }
    // One call either way keeps content at the same place in the composition, so its saved
    // state, such as the back stack, comes back after a language change.
    CompositionLocalProvider(
        *listOfNotNull(resources?.let { LocalResources provides it }).toTypedArray(),
        content = content
    )
}

/**
 * The locales to load and format the app's strings in. First, the device's first locale in the
 * strings' language and script, which keeps the device's region and settings such as a chosen
 * digit style, or else the strings' language itself. Then the device's other locales, so
 * Android can fall back to them if the app has no strings in the first, such as when a
 * translation's `strings_language` isn't a valid tag.
 */
internal fun stringsLocales(device: LocaleList, stringsLanguage: Locale): LocaleList {
    val deviceLocales = List(device.size(), device::get)
    val first = deviceLocales.firstOrNull {
        LocaleListCompat.matchesLanguageAndScript(stringsLanguage, it)
    } ?: stringsLanguage
    return LocaleList(first, *deviceLocales.filter { it != first }.toTypedArray())
}

@Composable
@ReadOnlyComposable
private fun stringsLanguage(): Locale =
    Locale.forLanguageTag(stringResource(R.string.strings_language))
