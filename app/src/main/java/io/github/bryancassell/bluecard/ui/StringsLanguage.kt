package io.github.bryancassell.bluecard.ui

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import io.github.bryancassell.bluecard.R
import java.util.Locale

/**
 * The language of the app's strings (`strings_language`). It isn't always the device's: the app
 * may not have strings for the device's language.
 */
@Composable
@ReadOnlyComposable
fun stringsLocale(): Locale = Locale.forLanguageTag(stringResource(R.string.strings_language))

/**
 * Provides [LocalResources] in the strings' language to [content], so every `stringResource`
 * and `pluralStringResource` formats numbers with its digits and picks plural forms by its
 * rules, and a sentence never mixes two languages.
 *
 * When one of the device's languages is the strings' language, its locale is used, keeping the
 * device's region and settings such as a chosen digit style.
 */
// Lint warns that an app bundle may not install the resources for a locale set at runtime.
// This locale is the language of the strings already shown, so their resources are installed.
@SuppressLint("AppBundleLocaleChanges")
@Composable
fun ProvideStringsLanguageResources(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val stringsLocale = stringsLocale()
    val resources = remember(context, configuration, stringsLocale) {
        val locale = configuration.locales.firstInLanguageOf(stringsLocale) ?: stringsLocale
        val stringsConfiguration = Configuration(configuration).apply { setLocale(locale) }
        context.createConfigurationContext(stringsConfiguration).resources
    }
    CompositionLocalProvider(LocalResources provides resources, content = content)
}

private fun LocaleList.firstInLanguageOf(locale: Locale): Locale? =
    (0 until size()).map(::get).firstOrNull { it.language == locale.language }
