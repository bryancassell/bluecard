package io.github.bryancassell.bluecard.di

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.bryancassell.bluecard.data.report.PdfDocumentWriter
import io.github.bryancassell.bluecard.data.report.PdfReportRepository
import io.github.bryancassell.bluecard.data.report.ReportPdfWriter
import io.github.bryancassell.bluecard.data.report.ReportRepository
import javax.inject.Inject
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Checks that Hilt provides the production report implementations. */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ReportModuleTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var reportRepository: ReportRepository

    @Inject
    lateinit var pdfWriter: ReportPdfWriter

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun reportRepository_isPdfReportRepository() {
        assertTrue(reportRepository is PdfReportRepository)
    }

    @Test
    fun pdfWriter_isPdfDocumentWriter() {
        assertTrue(pdfWriter is PdfDocumentWriter)
    }
}
