package io.github.bryancassell.bluecard.di

import io.github.bryancassell.bluecard.data.DocumentUri
import io.github.bryancassell.bluecard.data.Documents
import io.github.bryancassell.bluecard.data.report.ReportRepository
import io.github.bryancassell.bluecard.text.PhoneNumberFormat

// What iOS doesn't have yet.

/**
 * Makes no report on iOS, until #347. Share report opens nothing, and Save report leaves no
 * file: the file picker's empty document is deleted ([Documents.save] with no content).
 */
internal class UnfinishedReports(private val documents: Documents) : ReportRepository {
    override suspend fun createReportToShare(advancementId: String): DocumentUri? = null

    override suspend fun saveReport(advancementId: String, destination: DocumentUri) {
        documents.save(destination) { null }
    }
}

/** Leaves phone numbers as typed, until #350 chooses what formats them on iOS. */
internal object UnformattedPhoneNumbers : PhoneNumberFormat {
    override suspend fun format(number: String): String = number
}
