package io.github.bryancassell.bluecard.text

// Its letters, digits and collator are Java's, which run without Robolectric. Its break
// iterators are Android's ICU, which needs it (AndroidTextRulesTest).
actual fun platformTextRules(): TextRules = AndroidTextRules()
