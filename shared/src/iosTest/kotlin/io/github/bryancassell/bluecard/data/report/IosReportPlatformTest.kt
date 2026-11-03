package io.github.bryancassell.bluecard.data.report

import io.github.bryancassell.bluecard.data.FileUrl
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.report_kind
import io.github.bryancassell.bluecard.text.IosLocaleFormats
import io.github.bryancassell.bluecard.text.currentRegion
import io.github.bryancassell.bluecard.text.stringsLocale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSTemporaryDirectory

/** Runs on the main thread, as the screens ask for reports from it. */
class IosReportPlatformTest {
    @Test
    fun resourceEnvironment_readsTheAppsStrings() = runTest {
        val environment = IosReportPlatform.resourceEnvironment()

        assertEquals("Merit badge report", getString(environment, Res.string.report_kind))
    }

    // The app's only strings are English: its reports are in English, left to right, whatever the
    // device's language. Dates are written in the device's region, as on screen.
    @Test
    fun typesetter_isInTheStringsLocale() = runTest {
        val environment = IosReportPlatform.resourceEnvironment()

        val typesetter = assertIs<CoreTextTypesetter>(IosReportPlatform.typesetter(environment))

        assertEquals("en", typesetter.language)
        assertFalse(typesetter.rightToLeft)
        val inRegion = IosLocaleFormats(stringsLocale("en", currentRegion()))
        val date = LocalDate(2026, 9, 30)
        assertEquals(inRegion.formatDate(date), typesetter.formats.formatDate(date))
    }

    // iCloud backup leaves the temporary directory out.
    @Test
    fun shareFolder_isInTheTemporaryDirectory() {
        assertEquals(NSTemporaryDirectory().toPath() / "reports", IosReportPlatform.shareFolder)
    }

    @Test
    fun shareUri_isTheFilesUrl() {
        val file = IosReportPlatform.shareFolder / "Camping merit badge report.pdf"

        val uri = assertIs<FileUrl>(IosReportPlatform.shareUri(file))

        assertEquals(file.toString(), uri.url.path)
        assertEquals("file", uri.url.scheme)
    }
}
