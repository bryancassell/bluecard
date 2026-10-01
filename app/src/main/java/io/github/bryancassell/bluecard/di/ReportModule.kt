package io.github.bryancassell.bluecard.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.bryancassell.bluecard.data.report.PdfDocumentWriter
import io.github.bryancassell.bluecard.data.report.PdfReportRepository
import io.github.bryancassell.bluecard.data.report.ReportPdfWriter
import io.github.bryancassell.bluecard.data.report.ReportRepository

/**
 * Binds [ReportRepository] and the PDF writer it uses. Kept apart from DataModule so UI tests
 * can replace reports with a fake, since PdfDocument only runs on a device.
 */
@Module
@InstallIn(SingletonComponent::class)
interface ReportModule {
    @Binds
    fun bindReportRepository(repository: PdfReportRepository): ReportRepository

    @Binds
    fun bindReportPdfWriter(writer: PdfDocumentWriter): ReportPdfWriter
}
