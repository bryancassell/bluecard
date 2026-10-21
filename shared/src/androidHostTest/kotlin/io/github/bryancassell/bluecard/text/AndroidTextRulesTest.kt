package io.github.bryancassell.bluecard.text

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

// Robolectric, for Android's ICU.
@RunWith(AndroidJUnit4::class)
class AndroidTextRulesTest : TextRulesContract() {
    override val textRules = AndroidTextRules()
}
