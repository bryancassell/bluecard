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
import platform.Foundation.canonicalLanguageIdentifierFromString
import platform.Foundation.canonicalLocaleIdentifierFromString
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.localeIdentifier
import platform.Foundation.localeIdentifierFromComponents
import platform.Foundation.preferredLanguages
import platform.Foundation.scriptCode

// The language the app's strings are in, which isn't always the device's: the app may not have
// strings for the device's language (ARCHITECTURE.md, Language and layout direction). Compose
// code uses rememberLocaleFormats (ui/StringsLanguage.ios.kt), which builds on these.

private const val APPLE_LANGUAGES = "AppleLanguages"

/**
 * Saves the strings' locale ([stringsLocale]) as the app's first preferred language, so Compose
 * Multiplatform's strings pick their plural forms by its language's rules. It takes them from the
 * first of `NSLocale.preferredLanguages` (1.12.2), which is otherwise the device's: on an iPhone
 * set to French, which counts 0 as one, the English strings read "0 night". It's JetBrains'
 * workaround (https://kotlinlang.org/docs/multiplatform/compose-resource-environment.html), as
 * setting the default locale is on Android (`useStringsLocalesByDefault`). The device's languages
 * follow it, as on Android (`stringsLocales`), for whatever reads past the first.
 *
 * Material 3 reads it too: its labels are in the strings' language, as UIKit's own text already
 * is in an app with no strings in the device's language, and the date picker writes its dates,
 * and takes a typed one, in the device's region, as the app's own dates are ([IosLocaleFormats]).
 * Saved as "en", an English (UK) iPhone's picker took 03/11/2026 as March 11. It's saved in the
 * Gregorian calendar, such as en-GB-u-ca-gregory, since the picker writes its dates in the
 * locale's own calendar, which for en-IR would be the Persian one. A change of region while the
 * app runs reaches the picker at the next launch.
 *
 * iOS keeps it in the app's `AppleLanguages` user default, which outlasts the app, so the one
 * saved at the last launch is removed first. The strings' language is then read in the device's
 * own languages, and a change to them is seen at the next launch. The app calls this as it
 * starts, before Compose reads the language. Launching it with `-AppleLanguages`, as Xcode's App
 * Language option and UI tests can, overrides it, since launch arguments outrank what an app
 * saves.
 *
 * Saving it was chosen over setting it for each launch only, in the argument domain, on PR #397.
 * Once a translation is in the Xcode project, that has two costs to revisit. iOS keeps the
 * language a scout picks for the app in Settings in the same user default, so this undoes the
 * choice at each launch. And UIKit picks its language and layout direction from the saved list
 * as the app starts, before this runs, so after the device's language changes, they're a launch
 * behind the strings'.
 */
fun useStringsLanguageByDefault() =
    useStringsLanguageByDefault { runBlocking { getString(Res.string.strings_language) } }

/** [useStringsLanguageByDefault], with [stringsLanguage] reading the strings' language. */
internal fun useStringsLanguageByDefault(stringsLanguage: () -> String) {
    val defaults = NSUserDefaults.standardUserDefaults
    defaults.removeObjectForKey(APPLE_LANGUAGES)
    val first = gregorianLanguageTag(stringsLocale(stringsLanguage(), currentRegion()))
    val device = NSLocale.preferredLanguages.filterIsInstance<String>()
    defaults.setObject((listOf(first) + device).distinct(), APPLE_LANGUAGES)
}

/**
 * [locale] as a BCP 47 tag in the Gregorian calendar, such as en-GB-u-ca-gregory. Foundation
 * would write en_GB@calendar=gregorian as "en-GB@calendar=gregorian", which isn't one.
 */
internal fun gregorianLanguageTag(locale: NSLocale): String =
    NSLocale.canonicalLanguageIdentifierFromString(locale.localeIdentifier) + "-u-ca-gregory"

/** The device's Region setting, such as "FR". */
internal fun currentRegion(): String? = NSLocale.currentLocale.countryCode

/**
 * The locale the app's strings are formatted in: their language, [stringsLanguage] (a BCP 47 tag
 * such as "en"), in [region], the device's Region setting. iOS's own current locale is that for
 * an app with no strings in the device's language, en_FR on an iPhone set to French, but it takes
 * its language from the app's Xcode localizations rather than the strings, and it keeps the
 * digits a scout can choose in Settings. "%1$d" in a string inserts Latin digits, so the language's
 * own are used instead, and a sentence never mixes two kinds, as on Android (`stringsLocales`).
 *
 * A tag with a region of its own, such as zh-TW, keeps it, since the region can be what marks its
 * script or variant: zh in the US would be Simplified Chinese, and pt in the US Brazilian.
 */
internal fun stringsLocale(stringsLanguage: String, region: String?): NSLocale {
    val language = NSLocale(NSLocale.canonicalLocaleIdentifierFromString(stringsLanguage))
    if (language.countryCode != null) return language
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
 * Each is read with [currentRegion].
 */
internal fun deviceRegions(currentRegion: () -> String? = ::currentRegion): Flow<String?> =
    callbackFlow {
        val center = NSNotificationCenter.defaultCenter
        val observer = center.addObserverForName(NSCurrentLocaleDidChangeNotification, null, null) {
            trySend(currentRegion())
        }
        trySend(currentRegion())
        awaitClose { center.removeObserver(observer) }
    }
