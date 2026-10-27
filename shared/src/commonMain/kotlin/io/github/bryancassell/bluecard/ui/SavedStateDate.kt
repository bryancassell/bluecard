package io.github.bryancassell.bluecard.ui

import kotlinx.datetime.LocalDate

/**
 * The date kept in saved state as its epoch day, [value], or null if [value] is null. A value of
 * another kind, or a day outside the dates [LocalDate] can hold, is ignored, giving null too, as
 * [restoredText] ignores one: MainActivity keeps the extras of the intent that opened the app out
 * of the SavedStateHandles BlueCard creates, but one created another way could still get them.
 */
fun dateFromEpochDay(value: Any?): LocalDate? = (value as? Long)?.let {
    // kotlinx-datetime keeps LocalDate's first and last days internal, and fromEpochDays throws an
    // IllegalArgumentException for a day outside them (0.8.0).
    try {
        LocalDate.fromEpochDays(it)
    } catch (e: IllegalArgumentException) {
        null
    }
}
