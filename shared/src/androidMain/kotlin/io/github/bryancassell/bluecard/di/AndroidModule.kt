package io.github.bryancassell.bluecard.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.bryancassell.bluecard.data.backup.BackupRepository
import io.github.bryancassell.bluecard.data.backup.JsonBackupRepository
import io.github.bryancassell.bluecard.data.profile.DataStoreProfileRepository
import io.github.bryancassell.bluecard.data.progress.BlueCardDatabase
import io.github.bryancassell.bluecard.data.progress.DamagedProgressRepository
import io.github.bryancassell.bluecard.data.progress.FileDamagedProgressRepository
import io.github.bryancassell.bluecard.data.progress.SetAsideDamagedDatabaseDriver
import io.github.bryancassell.bluecard.data.report.PdfDocumentWriter
import io.github.bryancassell.bluecard.data.report.PdfReportRepository
import io.github.bryancassell.bluecard.data.report.ReportPdfWriter
import io.github.bryancassell.bluecard.data.report.ReportRepository
import io.github.bryancassell.bluecard.text.AndroidTextRules
import io.github.bryancassell.bluecard.text.TextRules
import kotlinx.coroutines.CoroutineScope
import okio.Path.Companion.toOkioPath
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.create
import org.koin.plugin.module.dsl.single

/** What the app is built from that needs Android to build, such as a [Context]. */
val androidModule = module {
    single<WarningLog> { LogcatWarningLog }
    single { create(::progressDatabase) }
    single { create(::profileDataStore) }

    // The database sets damaged progress aside with this same instance, so the notice shows
    // without the app reopening. So a test that fakes DamagedProgressRepository fakes
    // ProgressRepository too, or the real database tells the real one instead of the fake.
    single<FileDamagedProgressRepository>() bind DamagedProgressRepository::class
    single<PdfReportRepository>() bind ReportRepository::class
    single<PdfDocumentWriter>() bind ReportPdfWriter::class
    single<JsonBackupRepository>() bind BackupRepository::class
    single<AndroidTextRules>() bind TextRules::class
}

/** Writes warnings to logcat, which bug reports include. */
internal object LogcatWarningLog : WarningLog {
    override fun warn(tag: String, message: String, error: Throwable?) {
        Log.w(tag, message, error)
    }
}

private fun progressDatabase(
    context: Context,
    damagedProgress: FileDamagedProgressRepository
): BlueCardDatabase =
    Room.databaseBuilder(context, BlueCardDatabase::class.java, BlueCardDatabase.NAME)
        .setDriver(SetAsideDamagedDatabaseDriver(BundledSQLiteDriver(), damagedProgress))
        .build()

// One for the app: DataStore allows only one instance per file in a process.
private fun profileDataStore(
    context: Context,
    @ApplicationScope scope: CoroutineScope
): DataStore<Preferences> = DataStoreProfileRepository.createDataStore(
    scope = scope,
    produceFile = {
        context.preferencesDataStoreFile(DataStoreProfileRepository.FILE_NAME).toOkioPath()
    }
)
