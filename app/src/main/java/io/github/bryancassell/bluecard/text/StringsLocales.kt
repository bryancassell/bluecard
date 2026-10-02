package io.github.bryancassell.bluecard.text

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Configuration.SCREENLAYOUT_LAYOUTDIR_MASK
import android.content.res.Resources
import android.os.LocaleList
import androidx.core.os.LocaleListCompat
import io.github.bryancassell.bluecard.R
import java.util.Locale

// The language the app's strings are in, which isn't always the device's: the app may not have
// strings for the device's language (ARCHITECTURE.md, Language and layout direction). Compose
// code uses the functions in ui/StringsLanguage.kt, which build on these.

/**
 * The locale the app's strings are formatted in, from [stringsLocales], for code outside Compose,
 * such as an activity before it has content.
 */
fun stringsLocale(context: Context): Locale =
    stringsLocales(context.resources.configuration.locales, stringsLanguage(context.resources))[0]

/**
 * [context]'s resources in the strings' language ([stringsLocales]), for code outside Compose
 * that formats the app's strings, such as the PDF report. The first locale of their
 * configuration is [stringsLocale]'s, and their layout direction is its direction.
 */
fun stringsLanguageResources(context: Context): Resources = stringsLanguageResourcesOrNull(
    context,
    context.resources.configuration,
    stringsLanguage(context.resources),
    keepLayoutDirection = false
) ?: context.resources

/**
 * [context]'s resources for [configuration] with its locales set to [stringsLocales] for
 * [stringsLanguage], or null if it already has them, as on an English phone. Their layout
 * direction is the first locale's, or with [keepLayoutDirection], [configuration]'s. The
 * screens (`ProvideStringsLanguageResources`) and code outside Compose, such as the PDF report,
 * both make them here, so they format the app's strings the same way.
 */
// Lint warns that an app bundle may not install the resources for a locale set at runtime.
// These are the language of the strings already shown and the device's own locales, so their
// resources are installed.
@SuppressLint("AppBundleLocaleChanges")
internal fun stringsLanguageResourcesOrNull(
    context: Context,
    configuration: Configuration,
    stringsLanguage: Locale,
    keepLayoutDirection: Boolean
): Resources? {
    val locales = stringsLocales(configuration.locales, stringsLanguage)
    if (locales == configuration.locales) return null
    val stringsConfiguration = Configuration(configuration).apply {
        // setLocales also sets the layout direction from the first locale.
        setLocales(locales)
        if (keepLayoutDirection) {
            screenLayout = screenLayout and SCREENLAYOUT_LAYOUTDIR_MASK.inv() or
                (configuration.screenLayout and SCREENLAYOUT_LAYOUTDIR_MASK)
        }
    }
    return context.createConfigurationContext(stringsConfiguration).resources
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

/** The language of the app's strings, from [resources] (`strings_language`). */
internal fun stringsLanguage(resources: Resources): Locale =
    Locale.forLanguageTag(resources.getString(R.string.strings_language))
