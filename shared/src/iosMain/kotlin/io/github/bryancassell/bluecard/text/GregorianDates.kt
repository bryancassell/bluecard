package io.github.bryancassell.bluecard.text

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
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
internal fun LocalDate.toNSDate(calendar: NSCalendar): NSDate {
    val day = NSDateComponents().apply {
        year = this@toNSDate.year.toLong()
        month = this@toNSDate.month.number.toLong()
        this.day = this@toNSDate.day.toLong()
    }
    return calendar.dateFromComponents(day)!!
}

/** The day this is in, in [calendar], a [gregorianCalendar], in its time zone. */
internal fun NSDate.toLocalDate(calendar: NSCalendar): LocalDate {
    val day = calendar.components(
        NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay,
        this
    )
    return LocalDate(day.year.toInt(), day.month.toInt(), day.day.toInt())
}
