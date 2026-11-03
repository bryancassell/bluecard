package io.github.bryancassell.bluecard.data.report

import android.content.Context
import androidx.core.content.FileProvider
import io.github.bryancassell.bluecard.data.DocumentUri
import io.github.bryancassell.bluecard.text.stringsLocale
import okio.Path
import okio.Path.Companion.toOkioPath
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.getSystemResourceEnvironment

/**
 * Reports on Android: laid out with [StaticLayoutTypesetter], and shared from the cache folder's
 * `reports` folder, which `FileProvider` shares (`res/xml/report_paths.xml`) and Auto Backup
 * leaves out.
 */
class AndroidReportPlatform(private val context: Context) : ReportPlatform<StaticLayoutText> {
    // From the context's configuration, which any thread can read.
    override suspend fun resourceEnvironment() = getSystemResourceEnvironment()

    // The strings' locale comes from the configuration too, as the environment does.
    override suspend fun typesetter(environment: ResourceEnvironment) =
        StaticLayoutTypesetter(stringsLocale(context))

    override val shareFolder: Path get() = context.cacheDir.toOkioPath() / REPORTS_FOLDER

    // A content URI, which an app the report is shared with can read once it's granted
    // permission.
    override fun shareUri(file: Path): DocumentUri =
        FileProvider.getUriForFile(context, fileProviderAuthority(context), file.toFile())

    companion object {
        /** The cache folder that reports to share are written to. */
        const val REPORTS_FOLDER = "reports"

        /**
         * The authority of the `FileProvider` that shares reports, set in the manifest. It's
         * built from the application ID, because two installed apps can't declare the same one,
         * and a debug build installs next to a release build.
         */
        fun fileProviderAuthority(context: Context) = "${context.packageName}.reports"
    }
}
