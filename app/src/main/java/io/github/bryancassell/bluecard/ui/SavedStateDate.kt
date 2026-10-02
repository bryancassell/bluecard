package io.github.bryancassell.bluecard.ui

import java.time.LocalDate

/**
 * The date kept in saved state as its epoch day, [value], or null if [value] is null. A value of
 * another kind, or a day outside the dates [LocalDate] can hold, is ignored, giving null too, as
 * [restoredText] ignores one: MainActivity keeps the extras of the intent that opened the app out
 * of the SavedStateHandles BlueCard creates, but one created another way could still get them.
 */
fun dateFromEpochDay(value: Any?): LocalDate? =
    (value as? Long)?.takeIf { it in EPOCH_DAYS }?.let(LocalDate::ofEpochDay)

private val EPOCH_DAYS = LocalDate.MIN.toEpochDay()..LocalDate.MAX.toEpochDay()
