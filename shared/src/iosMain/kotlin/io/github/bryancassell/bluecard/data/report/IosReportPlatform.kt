package io.github.bryancassell.bluecard.data.report

import io.github.bryancassell.bluecard.data.DocumentUri
import io.github.bryancassell.bluecard.data.FileUrl
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.strings_language
import io.github.bryancassell.bluecard.text.EnglishLocaleFormats
import okio.Path
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL

/**
 * Reports on iOS: laid out with [CoreTextTypesetter], and shared from a `reports` folder in the
 * app's temporary directory, which iCloud backup leaves out. The share sheet reads a report
 * through its file URL.
 */
internal object IosReportPlatform : ReportPlatform<CoreTextText> {
    // In the strings' language, written as the screens write it: in English, left to right,
    // until #350 gives iOS the strings' locale.
    override suspend fun typesetter() = CoreTextTypesetter(
        formats = EnglishLocaleFormats,
        rightToLeft = false,
        language = getString(Res.string.strings_language)
    )

    override val shareFolder: Path get() = NSTemporaryDirectory().toPath() / "reports"

    override fun shareUri(file: Path): DocumentUri = FileUrl(NSURL.fileURLWithPath(file.toString()))
}
