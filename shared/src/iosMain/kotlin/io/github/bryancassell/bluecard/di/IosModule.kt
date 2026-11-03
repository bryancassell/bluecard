package io.github.bryancassell.bluecard.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.bryancassell.bluecard.data.Documents
import io.github.bryancassell.bluecard.data.FileCoordinatorDocuments
import io.github.bryancassell.bluecard.data.SystemFileCoordinator
import io.github.bryancassell.bluecard.data.profile.DataStoreProfileRepository
import io.github.bryancassell.bluecard.data.progress.BlueCardDatabase
import io.github.bryancassell.bluecard.data.progress.BlueCardDatabaseConstructor
import io.github.bryancassell.bluecard.data.progress.Converters
import io.github.bryancassell.bluecard.data.progress.DamagedProgressRepository
import io.github.bryancassell.bluecard.data.progress.FileDamagedProgressRepository
import io.github.bryancassell.bluecard.data.progress.SetAsideDamagedDatabaseDriver
import io.github.bryancassell.bluecard.data.report.CoreTextText
import io.github.bryancassell.bluecard.data.report.IosReportPlatform
import io.github.bryancassell.bluecard.data.report.PdfReportRepository
import io.github.bryancassell.bluecard.data.report.ReportPdfWriter
import io.github.bryancassell.bluecard.data.report.ReportPlatform
import io.github.bryancassell.bluecard.data.report.ReportRepository
import io.github.bryancassell.bluecard.data.report.UIGraphicsPdfWriter
import io.github.bryancassell.bluecard.text.IosTextRules
import io.github.bryancassell.bluecard.text.PhoneNumberFormat
import io.github.bryancassell.bluecard.text.TextRules
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.create
import org.koin.plugin.module.dsl.single
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSLog
import platform.Foundation.NSUserDomainMask

/** What the app is built from that needs iOS to build, such as where its files go. */
val iosModule = module {
    single<WarningLog> { NSLogWarningLog }
    single { create(::progressDatabase) }
    single { create(::profileDataStore) }

    // As on Android, the database sets damaged progress aside with this same instance, so the
    // notice shows without the app reopening.
    single { create(::damagedProgressRepository) } bind DamagedProgressRepository::class
    single<PdfReportRepository<CoreTextText>>() bind ReportRepository::class
    single<ReportPlatform<CoreTextText>> { IosReportPlatform }
    single<UIGraphicsPdfWriter>() bind ReportPdfWriter::class
    single { create(::fileCoordinatorDocuments) } bind Documents::class
    single<IosTextRules>() bind TextRules::class
    single<PhoneNumberFormat> { UnformattedPhoneNumbers }
}

/**
 * Writes warnings with NSLog, which goes to the unified log that Console and a sysdiagnose
 * show. Apple's `Logger` and `os_log` are Swift and C macros, which Kotlin can't call.
 */
internal object NSLogWarningLog : WarningLog {
    override fun warn(tag: String, message: String, error: Throwable?) {
        nsLog(listOfNotNull("$tag: $message", error?.stackTraceToString()).joinToString("\n"))
    }
}

/**
 * Writes [text] with NSLog, in as many messages as [logMessages] needs. Each is the format, with
 * each % doubled so it prints as is: Kotlin passes a string to a C function's variable arguments
 * as C text, not the NSString that %@ needs, which crashed the app.
 */
internal fun nsLog(text: String) {
    for (message in logMessages(text)) NSLog(message.replace("%", "%%"))
}

/**
 * [text] in messages of up to [maxBytes] bytes of UTF-8, which the unified log keeps whole. It
 * keeps only about the first 1,015 bytes of a message, then writes "<…>" (checked on an iOS 27
 * simulator), which would cut a stack trace off after its first frames. A message ends at the
 * end of a line where it can, so each frame stays on one line.
 */
internal fun logMessages(text: String, maxBytes: Int = 1000): List<String> {
    val messages = mutableListOf<String>()
    val message = StringBuilder()
    var bytes = 0
    var started = false
    for (piece in text.lines().flatMap { it.inPiecesOf(maxBytes) }) {
        val pieceBytes = piece.utf8Bytes()
        if (started && bytes + 1 + pieceBytes <= maxBytes) {
            message.append('\n').append(piece)
            bytes += 1 + pieceBytes
        } else {
            if (started) messages += message.toString()
            message.clear().append(piece)
            bytes = pieceBytes
            started = true
        }
    }
    messages += message.toString()
    return messages
}

// The line, cut between characters into pieces of up to maxBytes bytes if it's longer.
private fun String.inPiecesOf(maxBytes: Int): List<String> {
    if (utf8Bytes() <= maxBytes) return listOf(this)
    val pieces = mutableListOf<String>()
    var start = 0
    var bytes = 0
    var i = 0
    while (i < length) {
        val pair = this[i].isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate()
        val charBytes = when {
            pair -> 4
            this[i].code < 0x80 -> 1
            this[i].code < 0x800 -> 2
            else -> 3
        }
        if (bytes + charBytes > maxBytes) {
            pieces += substring(start, i)
            start = i
            bytes = 0
        }
        bytes += charBytes
        i += if (pair) 2 else 1
    }
    pieces += substring(start)
    return pieces
}

private fun String.utf8Bytes() = encodeToByteArray().size

private fun damagedProgressRepository(
    clock: ZonedClock,
    warningLog: WarningLog,
    @ApplicationScope scope: CoroutineScope,
    @IoDispatcher ioDispatcher: CoroutineDispatcher
) = FileDamagedProgressRepository(
    // On the database's file system, as it must be. iCloud backs Application Support up, which
    // the damaged copies must be left out of, so #349 marks their folder excluded from backup.
    produceDirectory = ::applicationSupportDirectory,
    clock = clock,
    warningLog = warningLog,
    externalScope = scope,
    ioDispatcher = ioDispatcher
)

private fun fileCoordinatorDocuments(
    warningLog: WarningLog,
    @IoDispatcher ioDispatcher: CoroutineDispatcher
) = FileCoordinatorDocuments(warningLog, ioDispatcher, SystemFileCoordinator)

private fun progressDatabase(damagedProgress: FileDamagedProgressRepository): BlueCardDatabase {
    val driver = SetAsideDamagedDatabaseDriver(BundledSQLiteDriver(), damagedProgress)
    return Room.databaseBuilder(
        name = (applicationSupportDirectory() / BlueCardDatabase.NAME).toString(),
        factory = BlueCardDatabaseConstructor::initialize
    )
        .setDriver(driver)
        .addColumnTypeConverter(Converters(driver::recordUnreadableValue))
        .build()
}

// One for the app: DataStore allows only one instance per file in a process. Its name ends as
// Android's does, which DataStore requires of a preferences file.
private fun profileDataStore(@ApplicationScope scope: CoroutineScope): DataStore<Preferences> =
    DataStoreProfileRepository.createDataStore(
        scope = scope,
        produceFile = {
            applicationSupportDirectory() / "${DataStoreProfileRepository.FILE_NAME}.preferences_pb"
        }
    )

/**
 * The app's Application Support directory, where the progress database, the profile and damaged
 * progress go. It's made if it isn't there yet, as it isn't when the app first starts. Apple's
 * File System Programming Guide names it for "app-created data files", which iCloud backs up,
 * and Documents for files the scout made, which the Files app can show:
 * https://developer.apple.com/library/archive/documentation/FileManagement/Conceptual/FileSystemProgrammingGuide/FileSystemOverview/FileSystemOverview.html
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun applicationSupportDirectory(): Path = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = error.ptr
    )
    val path = url?.path ?: throw IOException(
        "Couldn't make the Application Support directory: ${error.value?.localizedDescription}"
    )
    path.toPath()
}
