package io.github.bryancassell.bluecard.text

import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.strings_language
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSCurrentLocaleDidChangeNotification
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.NSLocaleLanguageCode
import platform.Foundation.NSLocaleScriptCode
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSUserDefaults
import platform.Foundation.canonicalLocaleIdentifierFromString
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.localeIdentifierFromComponents
import platform.Foundation.scriptCode

// The language the app's strings are in, which isn't always the device's: the app may not have
// strings for the device's language (ARCHITECTURE.md, Language and layout direction). Compose
// code uses rememberLocaleFormats (ui/StringsLanguage.ios.kt), which builds on these.

private const val APPLE_LANGUAGES = "AppleLanguages"

/**
 * Saves the strings' language (`strings_language`) as the app's only preferred language, so
 * Compose Multiplatform's strings pick their plural forms by its rules. It takes them from the
 * first of `NSLocale.preferredLanguages` (1.12.2), which is otherwise the device's: on an iPhone
 * set to French, which counts 0 as one, the English strings read "0 night". It's JetBrains'
 * workaround (https://kotlinlang.org/docs/multiplatform/compose-resource-environment.html), as
 * setting the default locale is on Android (`useStringsLocalesByDefault`). Material 3 takes the
 * language of its labels and date picker from it too, so they're in the strings' language, as
 * UIKit's own text already is in an app with no strings in the device's language.
 *
 * Only the language is saved, with no region: Material 3 writes the date picker's dates in the
 * calendar of the locale it reads, which for en-IR is the Persian one.
 *
 * iOS keeps it in the app's `AppleLanguages` user default, which outlasts the app, so the one
 * saved at the last launch is removed first. The strings' language is then read in the device's
 * own languages, and a change to them is seen at the next launch. The app calls this as it
 * starts, before Compose reads the language.
 */
fun useStringsLanguageByDefault() =
    useStringsLanguageByDefault { runBlocking { getString(Res.string.strings_language) } }

/** [useStringsLanguageByDefault], with [stringsLanguage] reading the strings' language. */
internal fun useStringsLanguageByDefault(stringsLanguage: () -> String) {
    val defaults = NSUserDefaults.standardUserDefaults
    defaults.removeObjectForKey(APPLE_LANGUAGES)
    defaults.setObject(listOf(stringsLanguage()), APPLE_LANGUAGES)
}

/**
 * The locale the app's strings are formatted in: their language, [stringsLanguage] (a BCP 47 tag
 * such as "en"), in [region], the device's Region setting. iOS's own current locale is that for
 * an app with no strings in the device's language, en_FR on an iPhone set to French, but it takes
 * its language from the app's Xcode localizations rather than the strings, and it keeps the
 * digits a scout can choose in Settings. "%1$d" in a string inserts Latin digits, so the language's
 * own are used instead, and a sentence never mixes two kinds, as on Android (`stringsLocales`).
 */
internal fun stringsLocale(stringsLanguage: String, region: String?): NSLocale {
    val language = NSLocale(NSLocale.canonicalLocaleIdentifierFromString(stringsLanguage))
    val components = listOfNotNull(
        NSLocaleLanguageCode to language.languageCode,
        language.scriptCode?.let { NSLocaleScriptCode to it },
        region?.let { NSLocaleCountryCode to it }
    ).toMap<Any?, Any?>()
    return NSLocale(NSLocale.localeIdentifierFromComponents(components))
}

/**
 * The device's region, such as "FR", and each one it changes to while the app runs. iOS's current
 * locale "does not change even when the person using the device changes region settings", Apple's
 * documentation of it says, but iOS posts [NSCurrentLocaleDidChangeNotification] when it does.
 */
internal fun deviceRegions(): Flow<String?> = callbackFlow {
    val center = NSNotificationCenter.defaultCenter
    val observer = center.addObserverForName(NSCurrentLocaleDidChangeNotification, null, null) {
        trySend(NSLocale.currentLocale.countryCode)
    }
    trySend(NSLocale.currentLocale.countryCode)
    awaitClose { center.removeObserver(observer) }
}
