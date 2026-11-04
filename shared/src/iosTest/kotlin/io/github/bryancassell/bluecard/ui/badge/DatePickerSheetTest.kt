package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.ui.graphics.Color
import io.github.bryancassell.bluecard.text.gregorianCalendar
import io.github.bryancassell.bluecard.text.toLocalDate
import io.github.bryancassell.bluecard.text.toNSDate
import io.github.bryancassell.bluecard.ui.theme.BlueCardDarkColorScheme
import io.github.bryancassell.bluecard.ui.theme.BlueCardLightColorScheme
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.datetime.LocalDate
import platform.CoreGraphics.CGFloatVar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.defaultTimeZone
import platform.Foundation.localeIdentifier
import platform.Foundation.setDefaultTimeZone
import platform.Foundation.timeZoneWithName
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIColor
import platform.UIKit.UIDatePickerMode
import platform.UIKit.UIDatePickerStyle
import platform.UIKit.UITraitCollection
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIViewController
import platform.UIKit.navigationItem
import platform.UIKit.presentationController
import platform.UIKit.resolvedColorWithTraitCollection

// The sheet can't be shown here, with no app window, so these tap its buttons, and swipe it
// down, by calling what UIKit would.
@OptIn(ExperimentalForeignApi::class)
class DatePickerSheetTest {
    private val today = LocalDate(2026, 5, 20)
    private val light = UIUserInterfaceStyle.UIUserInterfaceStyleLight
    private val dark = UIUserInterfaceStyle.UIUserInterfaceStyleDark
    private val events = mutableListOf<String>()

    // West of UTC, where the evening is already the next day in UTC. Each test runs in it, so
    // they pass in any time zone the simulator is in.
    private val timeZone = assertNotNull(NSTimeZone.timeZoneWithName("America/Los_Angeles"))
    private val deviceTimeZone = NSTimeZone.defaultTimeZone

    // The days as the device's calendar has them.
    private val calendar = gregorianCalendar(timeZone)

    @BeforeTest
    fun setTimeZone() {
        NSTimeZone.setDefaultTimeZone(timeZone)
    }

    @AfterTest
    fun restoreTimeZone() {
        NSTimeZone.setDefaultTimeZone(deviceTimeZone)
    }

    private fun sheet(initial: LocalDate = LocalDate(2026, 5, 18), locale: String = "en_US") =
        DatePickerSheet(
            initial = initial,
            today = today,
            locale = NSLocale(locale),
            onConfirm = { events += "picked $it" },
            onCancel = { events += "cancelled" }
        )

    @Test
    fun picker_isIOSsInlineCalendar_ofDays() {
        val picker = sheet().picker

        assertEquals(UIDatePickerStyle.UIDatePickerStyleInline, picker.preferredDatePickerStyle)
        assertEquals(UIDatePickerMode.UIDatePickerModeDate, picker.datePickerMode)
    }

    @Test
    fun picker_startsAtTheInitialDate() {
        assertEquals(LocalDate(2026, 5, 18), sheet().picker.date.toLocalDate(calendar))
    }

    // As when the date was recorded while the device's clock was ahead.
    @Test
    fun picker_withAnInitialDateAfterToday_startsAtToday() {
        val picker = sheet(initial = LocalDate(2026, 6, 1)).picker

        assertEquals(today, picker.date.toLocalDate(calendar))
    }

    @Test
    fun picker_offersNoDayAfterToday() {
        val picker = sheet().picker

        assertEquals(today, assertNotNull(picker.maximumDate).toLocalDate(calendar))
    }

    // The picker marks the day it's in there as today, which is the app's today. In UTC, it
    // marked tomorrow every evening west of UTC.
    @Test
    fun picker_isInTheGregorianCalendar_inTheDevicesTimeZone() {
        val picker = sheet().picker

        assertEquals(NSCalendarIdentifierGregorian, picker.calendar.calendarIdentifier)
        assertEquals(timeZone.name, picker.calendar.timeZone.name)
        assertEquals(timeZone.name, picker.timeZone?.name)
    }

    @Test
    fun picker_isInTheLocaleGiven() {
        assertEquals("en_IR", sheet(locale = "en_IR").picker.locale?.localeIdentifier)
    }

    @Test
    fun done_givesTheDayPicked() {
        val sheet = sheet()
        sheet.picker.date = LocalDate(2026, 5, 2).toNSDate(sheet.picker.calendar)

        sheet.doneButton.primaryAction!!.performWithSender(null, target = null)

        assertEquals(listOf("picked 2026-05-02"), events)
    }

    @Test
    fun cancel_givesNoDay() {
        val sheet = sheet()

        sheet.cancelButton.primaryAction!!.performWithSender(null, target = null)

        assertEquals(listOf("cancelled"), events)
    }

    @Test
    fun swipingTheSheetDown_cancels() {
        val controller = sheet().controller
        val presentation = assertNotNull(controller.presentationController)
        val delegate = presentation.delegate as UIAdaptivePresentationControllerDelegateProtocol

        delegate.presentationControllerDidDismiss(presentation)

        assertEquals(listOf("cancelled"), events)
    }

    @Test
    fun bar_hasCancelFirst_thenDone() {
        val sheet = sheet()
        val page = sheet.controller.viewControllers.single() as UIViewController

        // Equal, as Objective-C compares objects: Kotlin may wrap the same item twice.
        assertEquals(sheet.cancelButton, page.navigationItem.leftBarButtonItem)
        assertEquals(sheet.doneButton, page.navigationItem.rightBarButtonItem)
    }

    @Test
    fun tint_isTheAppsPrimaryColor() {
        val tint = assertNotNull(sheet().controller.view.tintColor)

        assertColor(BlueCardLightColorScheme.primary, tint, light)
        assertColor(BlueCardDarkColorScheme.primary, tint, dark)
    }

    // Under Done's white check, the dark scheme's primary was about 2:1.
    @Test
    fun done_isThePrimaryColor_orInDarkModeThePrimaryContainer() {
        val tint = assertNotNull(sheet().doneButton.tintColor)

        assertColor(BlueCardLightColorScheme.primary, tint, light)
        assertColor(BlueCardDarkColorScheme.primaryContainer, tint, dark)
    }

    private fun assertColor(expected: Color, color: UIColor, style: UIUserInterfaceStyle) {
        val traits = UITraitCollection.traitCollectionWithUserInterfaceStyle(style)
        val resolved = color.resolvedColorWithTraitCollection(traits)
        memScoped {
            val red = alloc<CGFloatVar>()
            val green = alloc<CGFloatVar>()
            val blue = alloc<CGFloatVar>()
            assertTrue(resolved.getRed(red.ptr, green.ptr, blue.ptr, alpha = null))
            assertEquals(expected.red.toDouble(), red.value, 0.001, "$style")
            assertEquals(expected.green.toDouble(), green.value, 0.001, "$style")
            assertEquals(expected.blue.toDouble(), blue.value, 0.001, "$style")
        }
    }
}
