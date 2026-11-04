package io.github.bryancassell.bluecard.text

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.toKotlinTimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toNSDateComponents
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.timeZoneForSecondsFromGMT

/**
 * The Gregorian calendar, which the dates scouts record are in, whatever the locale's own
 * calendar is, as Android's java.time writes them. Iran's, for one, is the Persian calendar, so
 * without it, en_IR writes Apr 15, 2026 as "Farvardin 26, 1405 AP". Its days start at midnight
 * in [timeZone]. In UTC, the default, a date reads as the same day in any time zone.
 */
internal fun gregorianCalendar(
    timeZone: NSTimeZone = NSTimeZone.timeZoneForSecondsFromGMT(0)
): NSCalendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian).apply {
    this.timeZone = timeZone
}

/** The start of this day in [calendar], a [gregorianCalendar], in its time zone. */
internal fun LocalDate.toNSDate(calendar: NSCalendar): NSDate =
    calendar.dateFromComponents(toNSDateComponents())!!

/** The day this is in, in [calendar]'s time zone. */
internal fun NSDate.toLocalDate(calendar: NSCalendar): LocalDate =
    toKotlinInstant().toLocalDateTime(calendar.timeZone.toKotlinTimeZone()).date
