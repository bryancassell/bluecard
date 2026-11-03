package io.github.bryancassell.bluecard.data.report

import io.github.bryancassell.bluecard.data.DocumentUri
import io.github.bryancassell.bluecard.data.FileUrl
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.strings_language
import io.github.bryancassell.bluecard.text.EnglishLocaleFormats
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

    // In the strings' language, written as the screens write it: in English, left to right,
    // until #350 gives iOS the strings' locale.
    override suspend fun typesetter(environment: ResourceEnvironment) = CoreTextTypesetter(
        formats = EnglishLocaleFormats,
        rightToLeft = false,
        language = getString(environment, Res.string.strings_language)
    )

    override val shareFolder: Path get() = NSTemporaryDirectory().toPath() / "reports"

    override fun shareUri(file: Path): DocumentUri = FileUrl(NSURL.fileURLWithPath(file.toString()))
}
