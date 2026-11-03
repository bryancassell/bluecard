package io.github.bryancassell.bluecard.data.report

import io.github.bryancassell.bluecard.data.DocumentUri
import io.github.bryancassell.bluecard.data.FileUrl
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.strings_language
import io.github.bryancassell.bluecard.text.IosLocaleFormats
import io.github.bryancassell.bluecard.text.currentRegion
import io.github.bryancassell.bluecard.text.isRightToLeft
import io.github.bryancassell.bluecard.text.stringsLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL

/**
 * Reports on iOS: laid out with [CoreTextTypesetter], and shared from a `reports` folder in the
 * app's temporary directory, which iCloud backup leaves out. The share sheet reads a report
 * through its file URL.
 */
internal object IosReportPlatform : ReportPlatform<CoreTextText> {
    // On the main thread: Compose Multiplatform 1.12.2 reads it from UIScreen.mainScreen's trait
    // collection on iOS (seen in its iOS library), which UIKit allows only there. From the main
    // thread, as the screens ask for reports, it doesn't wait.
    override suspend fun resourceEnvironment() =
        withContext(Dispatchers.Main.immediate) { getSystemResourceEnvironment() }

    // In the strings' locale, in the device's region, and in their language's direction, as the
    // screens write and lay out text (rememberLocaleFormats).
    override suspend fun typesetter(environment: ResourceEnvironment): CoreTextTypesetter {
        val language = getString(environment, Res.string.strings_language)
        return CoreTextTypesetter(
            formats = IosLocaleFormats(stringsLocale(language, currentRegion())),
            rightToLeft = isRightToLeft(language),
            language = language
        )
    }

    override val shareFolder: Path get() = NSTemporaryDirectory().toPath() / "reports"

    override fun shareUri(file: Path): DocumentUri = FileUrl(NSURL.fileURLWithPath(file.toString()))
}
