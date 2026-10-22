package io.github.bryancassell.bluecard.text

import android.content.Context
import android.os.LocaleList
import androidx.core.os.LocaleListCompat
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.strings_language
import java.util.Locale
import org.jetbrains.compose.resources.getString

// The language the app's strings are in, which isn't always the device's: the app may not have
// strings for the device's language (ARCHITECTURE.md, Language and layout direction). Compose
// code uses the functions in ui/StringsLanguage.kt, which build on these.

/**
 * Makes the strings' locales ([stringsLocales]) the default, and returns the first, which the
 * strings are formatted in. Compose Multiplatform's strings, in Compose and outside it, pick
 * their plural forms by the default locale's rules (1.12.2), so on a French phone, English
 * strings read "0 merit badges", not "0 merit badge". It has no public way to give them another
 * language; JetBrains' workaround sets the default locale too
 * (https://kotlinlang.org/docs/multiplatform/compose-resource-environment.html).
 *
 * The configuration keeps the device's locales, so text that isn't the app's strings follows the
 * device's language, such as the text selection toolbar and right-click menu, Material 3's
 * labels, the role and state names TalkBack reads, and the date picker
 * (`CompletionDatePickerDialog`).
 *
 * Android sets the default back to the device's on every configuration change (AOSP's
 * `ConfigurationController.updateLocaleListFromAppContext`), so `MainActivity` calls this each
 * time it's created, and `BlueCardApplication` after each change it's told of. A change only to
 * fields apps aren't told of, such as a window's position, sets it back unannounced, so plurals
 * shown after one, on screen or in a report, follow the device's rules until the next of those.
 */
suspend fun useStringsLocalesByDefault(context: Context): Locale {
    val locales = stringsLocales(context.resources.configuration.locales, stringsLanguage())
    LocaleList.setDefault(locales)
    return locales[0]
}

/**
 * The locale the app's strings are formatted in, from [stringsLocales], for code outside Compose,
 * such as the PDF report.
 */
suspend fun stringsLocale(context: Context): Locale =
    stringsLocales(context.resources.configuration.locales, stringsLanguage())[0]

/**
 * The locales to load and format the app's strings in. First, the device's first locale in the
 * strings' language and script, which keeps the device's region, or else the strings' language
 * itself. Then the device's other locales, so what reads the default locales
 * ([useStringsLocalesByDefault]) still finds them after the first.
 *
 * The first leaves out a choice of digits the device's locale makes ("-u-nu-"), as for English
 * with Arabic digits. "%1$d" in a string inserts Latin digits, so dates and totals use the
 * language's own digits too, and a sentence never mixes two kinds.
 * They're no fallbacks for a missing translation, as they were for Android's resources: Compose
 * Multiplatform picks a translation by the first only (ARCHITECTURE.md, Language and layout
 * direction).
 */
internal fun stringsLocales(device: LocaleList, stringsLanguage: Locale): LocaleList {
    val deviceLocales = List(device.size(), device::get)
    val match = deviceLocales.firstOrNull {
        LocaleListCompat.matchesLanguageAndScript(stringsLanguage, it)
    }
    val first = match?.let {
        Locale.Builder().setLocale(it).setUnicodeLocaleKeyword("nu", null).build()
    } ?: stringsLanguage
    // Without its digits, the first can be another of the device's locales, which a LocaleList
    // can't hold twice: Android 11, for one, throws for it.
    val others = deviceLocales.filter { it != match && it != first }
    return LocaleList(first, *others.toTypedArray())
}

/**
 * The language of the app's strings (`strings_language`), in the strings Compose Multiplatform
 * picks for the default locale. Those are the device's until [useStringsLocalesByDefault] sets
 * them, and then the strings' own, which pick the same strings.
 */
internal suspend fun stringsLanguage(): Locale =
    Locale.forLanguageTag(getString(Res.string.strings_language))
