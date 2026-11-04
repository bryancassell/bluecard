package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.text.gregorianCalendar
import io.github.bryancassell.bluecard.text.toLocalDate
import io.github.bryancassell.bluecard.text.toNSDate
import io.github.bryancassell.bluecard.ui.theme.BlueCardDarkColorScheme
import io.github.bryancassell.bluecard.ui.theme.BlueCardLightColorScheme
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
import platform.Foundation.localeIdentifier
import platform.Foundation.secondsFromGMT
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
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
    private val calendar = gregorianCalendar()
    private val events = mutableListOf<String>()

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

    // Rather than the device's calendar, which is the region's, such as Iran's Persian one
    // (#398), in the device's time zone. The simulator's region can't be changed from a test, so
    // the region's own calendar is checked on the simulator.
    @Test
    fun picker_isInTheGregorianCalendar_inUtc() {
        val picker = sheet().picker

        assertEquals(NSCalendarIdentifierGregorian, picker.calendar.calendarIdentifier)
        assertEquals(0, picker.calendar.timeZone.secondsFromGMT)
        assertEquals(0, assertNotNull(picker.timeZone).secondsFromGMT)
    }

    @Test
    fun picker_isInTheLocaleGiven() {
        assertEquals("en_IR", sheet(locale = "en_IR").picker.locale?.localeIdentifier)
    }

    @Test
    fun done_givesTheDayPicked() {
        val sheet = sheet()
        sheet.picker.date = LocalDate(2026, 5, 2).toNSDate(calendar)

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
    fun tint_isTheAppsPrimaryColor_inLightAndDarkMode() {
        val tint = assertNotNull(sheet().controller.view.tintColor)

        for ((style, scheme) in listOf(
            UIUserInterfaceStyle.UIUserInterfaceStyleLight to BlueCardLightColorScheme,
            UIUserInterfaceStyle.UIUserInterfaceStyleDark to BlueCardDarkColorScheme
        )) {
            val traits = UITraitCollection.traitCollectionWithUserInterfaceStyle(style)
            val color = tint.resolvedColorWithTraitCollection(traits)
            memScoped {
                val red = alloc<CGFloatVar>()
                val green = alloc<CGFloatVar>()
                val blue = alloc<CGFloatVar>()
                assertTrue(color.getRed(red.ptr, green.ptr, blue.ptr, alpha = null))
                assertEquals(scheme.primary.red.toDouble(), red.value, 0.001, "$style")
                assertEquals(scheme.primary.green.toDouble(), green.value, 0.001, "$style")
                assertEquals(scheme.primary.blue.toDouble(), blue.value, 0.001, "$style")
            }
        }
    }
}
