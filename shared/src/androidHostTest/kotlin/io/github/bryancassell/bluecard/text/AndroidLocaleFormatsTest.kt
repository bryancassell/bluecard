package io.github.bryancassell.bluecard.text

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import org.junit.runner.RunWith

// Robolectric, for Android's ICU, and because BidiFormatter reads the locale's direction from
// Android's TextUtils.
@RunWith(AndroidJUnit4::class)
class AndroidLocaleFormatsTest : LocaleFormatsContract() {
    override fun formats(languageTag: String) =
        AndroidLocaleFormats(Locale.forLanguageTag(languageTag))
}
