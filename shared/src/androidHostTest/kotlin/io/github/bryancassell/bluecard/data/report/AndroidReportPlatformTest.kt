package io.github.bryancassell.bluecard.data.report

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class AndroidReportPlatformTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val platform = AndroidReportPlatform(context)

    // FileProvider keeps each authority's folders from the first time it's used, while
    // Robolectric gives each test new ones. Starting it, as the system does when the app starts,
    // makes it read them again.
    @Before
    fun startFileProvider() {
        val authority = AndroidReportPlatform.fileProviderAuthority(context)
        val info = context.packageManager.resolveContentProvider(authority, 0)!!
        Robolectric.buildContentProvider(FileProvider::class.java).create(info)
    }

    @Test
    fun shareFolder_isTheCacheFoldersReportsFolder() {
        assertEquals(File(context.cacheDir, "reports").toOkioPath(), platform.shareFolder)
    }

    // res/xml/report_paths.xml shares that folder.
    @Test
    fun shareUri_isTheReportsProvidersContentUri_namedAfterTheFile() {
        val uri = platform.shareUri(platform.shareFolder / "Camping merit badge report.pdf")

        assertEquals("content", uri.scheme)
        assertEquals(AndroidReportPlatform.fileProviderAuthority(context), uri.authority)
        assertEquals("Camping merit badge report.pdf", uri.lastPathSegment)
    }

    // The app has no Persian strings, so its reports are in English, laid out left to right.
    @Config(qualifiers = "fa")
    @Test
    fun typesetter_onPersianPhone_isInTheStringsLocale() = runTest {
        val typesetter = platform.typesetter(platform.resourceEnvironment())

        assertFalse(typesetter.rightToLeft)
        assertEquals("Sep 30, 2026", typesetter.formats.formatDate(LocalDate(2026, 9, 30)))
    }

    @Config(qualifiers = "en-rGB")
    @Test
    fun typesetter_followsThePhonesRegion() = runTest {
        val formats = platform.typesetter(platform.resourceEnvironment()).formats

        assertEquals("30 Sept 2026", formats.formatDate(LocalDate(2026, 9, 30)))
    }
}
