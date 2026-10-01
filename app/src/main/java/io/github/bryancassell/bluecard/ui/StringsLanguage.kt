package io.github.bryancassell.bluecard.ui

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.content.res.Configuration.SCREENLAYOUT_LAYOUTDIR_MASK
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import io.github.bryancassell.bluecard.text.stringsLanguage
import io.github.bryancassell.bluecard.text.stringsLocales
import java.util.Locale

/**
 * The locale the app's strings are formatted in, from [stringsLocales]. It's in the language
 * of the strings (`strings_language`), which isn't always the device's: the app may not have
 * strings for the device's language. Code outside Compose uses `text.stringsLocale(context)`.
 */
@Composable
@ReadOnlyComposable
fun stringsLocale(): Locale =
    stringsLocales(LocalConfiguration.current.locales, stringsLanguage())[0]

/**
 * Provides [LocalResources] in the strings' language to [content], so every `stringResource`
 * and `pluralStringResource` formats numbers with its digits and picks plural forms by its
 * rules, and a sentence never mixes two languages.
 *
 * Labels the app doesn't read through [LocalResources] still follow the device's language, such
 * as the text selection toolbar (Cut, Copy, Paste), Material 3's labels and the role and state
 * names TalkBack reads, and are laid out in the activity's direction. Compose Foundation's
 * right-click menu does read [LocalResources], so it follows the strings' language.
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

@Composable
@ReadOnlyComposable
private fun stringsLanguage(): Locale = stringsLanguage(LocalResources.current)
