package io.github.bryancassell.bluecard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.report.PdfReportRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The FileProvider that shares reports, as the app's merged manifest declares it from :shared's.
 * :shared's own tests run as its test package, so only here does the authority show it's built
 * from the app's application ID (#245).
 */
@RunWith(AndroidJUnit4::class)
class ReportsProviderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun reportsProvider_isNamedAfterTheApplicationId() {
        val authority = PdfReportRepository.fileProviderAuthority(context)

        val provider = context.packageManager.resolveContentProvider(authority, 0)

        // Local tests run against the debug build, whose application ID ends in ".debug".
        assertEquals("io.github.bryancassell.bluecard.debug.reports", provider?.authority)
        assertEquals("androidx.core.content.FileProvider", provider?.name)
    }
}
