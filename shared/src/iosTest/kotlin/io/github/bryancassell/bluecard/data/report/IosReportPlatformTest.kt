package io.github.bryancassell.bluecard.data.report

import io.github.bryancassell.bluecard.data.FileUrl
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.report_kind
import io.github.bryancassell.bluecard.text.EnglishLocaleFormats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
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

    // The app's only strings are English, so until #350 reports are in English, left to right.
    @Test
    fun typesetter_isInTheStringsLanguage_leftToRight() = runTest {
        val environment = IosReportPlatform.resourceEnvironment()

        val typesetter = assertIs<CoreTextTypesetter>(IosReportPlatform.typesetter(environment))

        assertEquals("en", typesetter.language)
        assertFalse(typesetter.rightToLeft)
        assertSame(EnglishLocaleFormats, typesetter.formats)
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
