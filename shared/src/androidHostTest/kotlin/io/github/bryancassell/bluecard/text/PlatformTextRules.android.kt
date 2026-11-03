package io.github.bryancassell.bluecard.text

// Its letters and digits are Java's, which run without Robolectric. Its collator and break
// iterators are Android's ICU, which needs it (AndroidTextRulesTest).
actual fun platformTextRules(): TextRules = AndroidTextRules()
