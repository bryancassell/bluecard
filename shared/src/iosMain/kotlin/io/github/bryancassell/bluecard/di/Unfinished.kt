package io.github.bryancassell.bluecard.di

import io.github.bryancassell.bluecard.data.DocumentUri
import io.github.bryancassell.bluecard.data.Documents
import io.github.bryancassell.bluecard.data.report.ReportRepository
import io.github.bryancassell.bluecard.text.PhoneNumberFormat
import okio.IOException
import okio.Source

// What iOS doesn't have yet. Nothing calls these but the screens' Share, Save report, Export and
// Import, which find no app that can on iOS (rememberOtherApps, rememberCreateDocument).

/** Makes no report on iOS, until #347. */
internal object UnfinishedReports : ReportRepository {
    override suspend fun createReportToShare(advancementId: String): DocumentUri? = null

    override suspend fun saveReport(advancementId: String, destination: DocumentUri) {}
}

/** Reaches no document on iOS, until #348 adds the file picker. */
internal object UnfinishedDocuments : Documents {
    override suspend fun save(destination: DocumentUri, content: suspend () -> ByteArray?): Unit =
        throw IOException("iOS has no file picker yet (#348)")

    override fun open(source: DocumentUri): Source =
        throw IOException("iOS has no file picker yet (#348)")
}

/** Leaves phone numbers as typed, until #350 chooses what formats them on iOS. */
internal object UnformattedPhoneNumbers : PhoneNumberFormat {
    override suspend fun format(number: String): String = number
}
