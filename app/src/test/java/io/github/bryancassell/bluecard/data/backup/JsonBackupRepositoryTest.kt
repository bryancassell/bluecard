package io.github.bryancassell.bluecard.data.backup

import android.Manifest
import android.content.Context
import android.content.pm.ProviderInfo
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.catalog.FakeCatalogRepository
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Rank
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType.NUMBER
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType.TEXT
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition
import io.github.bryancassell.bluecard.data.profile.FakeProfileRepository
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.BadgeStart
import io.github.bryancassell.bluecard.data.progress.BlueCardDatabase
import io.github.bryancassell.bluecard.data.progress.Counselor
import io.github.bryancassell.bluecard.data.progress.FakeProgressRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.RoomProgressRepository
import io.github.bryancassell.bluecard.testing.FolderDocumentsProvider
import java.io.File
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/** Exports to and imports from documents in a documents provider, as the file picker gives them. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class JsonBackupRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val start = BadgeStart(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1))
    private val day = LocalDate.of(2026, 4, 15)
    private val profile = Profile("Alex Scout", "Troop 12")

    private fun badge(id: String, vararg requirements: Requirement) = MeritBadge(
        id = id,
        name = id,
        summary = "Our summary.",
        officialUrl = "https://www.scouting.org/merit-badges/$id/",
        requirementVersions = listOf(
            RequirementsVersion(start.requirementsVersion, requirements.toList())
        )
    )

    private fun tracker(column: String, type: TrackerColumnType, rowCount: Int? = null) =
        TrackerDefinition(listOf(TrackerColumn(column, column, type)), "row", "rows", rowCount)

    private val catalogRepository = FakeCatalogRepository(
        listOf(
            badge(
                "camping",
                Requirement("4b", "Pitch a tent."),
                Requirement("5", "Cook."),
                Requirement("6", "Plan a trip."),
                Requirement("9a", "Log nights.", tracker = tracker("nights", NUMBER)),
                Requirement(
                    "9b",
                    "Camp three times.",
                    tracker = tracker("place", TEXT, rowCount = 3)
                )
            ),
            badge("hiking"),
            badge("swimming")
        ),
        listOf(
            Rank(
                id = "tenderfoot",
                name = "Tenderfoot",
                summary = "Our summary.",
                officialUrl = "https://www.scouting.org/tenderfoot/",
                requirementVersions = listOf(
                    RequirementsVersion(
                        start.requirementsVersion,
                        listOf(Requirement("1a", "Pack for a campout."))
                    )
                )
            )
        )
    )
    private val profileRepository = FakeProfileRepository(profile)
    private val progressRepository = FakeProgressRepository()

    // As the app's scope, which outlives the screens.
    private val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())

    private fun newRepository(
        profileRepository: ProfileRepository = this.profileRepository,
        progressRepository: ProgressRepository = this.progressRepository,
        ioDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
        externalScope: CoroutineScope = this.externalScope
    ) = JsonBackupRepository(
        context,
        catalogRepository,
        profileRepository,
        progressRepository,
        ioDispatcher,
        externalScope
    )

    private val repository = newRepository()

    private val databases = mutableListOf<BlueCardDatabase>()

    // As the app's scope, for Room's writes.
    private val roomScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        externalScope.cancel()
        roomScope.cancel()
        databases.forEach { it.close() }
    }

    private val documents = "io.github.bryancassell.bluecard.test.documents"

    // Like the provider the system file picker saves to and opens from.
    private val documentsProvider = Robolectric.buildContentProvider(
        FolderDocumentsProvider::class.java
    ).create(
        ProviderInfo().apply {
            authority = documents
            exported = true
            grantUriPermissions = true
            readPermission = Manifest.permission.MANAGE_DOCUMENTS
            writePermission = Manifest.permission.MANAGE_DOCUMENTS
        }
    ).get()

    @Before
    fun setUpDocuments() {
        documentsProvider.folder = folder.newFolder("documents")
    }

    /**
     * A document holding [bytes]: empty, as the file picker makes one to export to, or a file
     * the scout chose to import.
     */
    private fun document(bytes: ByteArray = byteArrayOf()): Pair<Uri, File> {
        val file = File(documentsProvider.folder, "BlueCard export.json")
        file.writeBytes(bytes)
        return DocumentsContract.buildDocumentUri(documents, file.name) to file
    }

    private fun document(text: String) = document(text.encodeToByteArray())

    /** What [file], written by an export, holds. */
    private fun readExport(file: File) = decodeBackup(file.readText(), catalogRepository.badges)

    @Before
    fun recordProgress() = runTest {
        progressRepository.markRequirementCompleted("camping", "4b", day, start)
        progressRepository.setRequirementSignOffAndComment(
            "camping",
            "6",
            null,
            "Next trip.",
            start
        )
        progressRepository.addTrackerEntry(
            "camping",
            "9a",
            null,
            mapOf("nights" to "2"),
            day,
            start
        )
        progressRepository.setCompletedOnPriorDate("swimming", day, start)
    }

    /** Everything the fakes hold, as an import of an export of it would give it back. */
    private suspend fun storedBackup() =
        Backup(profileRepository.observeProfile().first()!!, withoutEntryIds(progressRepository))

    @Test
    fun exportBackup_writesTheProfileAndAllProgress_toTheDestination() = runTest {
        val (destination, file) = document()

        repository.exportBackup(destination)

        assertEquals(BackupReadResult.Valid(storedBackup()), readExport(file))
    }

    // The scout could keep an empty file without noticing that it failed.
    @Test
    fun exportBackup_whenProgressCantBeRead_throwsIOException_andDeletesTheDocument() = runTest {
        val (destination, file) = document()
        progressRepository.failLoads = true

        assertThrows<IOException> { repository.exportBackup(destination) }
        assertFalse(file.exists())
    }

    @Test
    fun exportBackup_whenTheDocumentsProviderRefuses_throwsIOException() = runTest {
        val (destination, _) = document()
        documentsProvider.refuseOpening = true

        assertThrows<IOException> { repository.exportBackup(destination) }
    }

    // Data management opens only once the profile is saved, so this is a bug, which crashes the
    // app as other bugs do.
    @Test
    fun exportBackup_withNoProfile_throwsIllegalState_andCrashesTheApp() = runTest {
        val crash = CompletableDeferred<Throwable>()
        val appScope = CoroutineScope(
            SupervisorJob() +
                UnconfinedTestDispatcher() +
                CoroutineExceptionHandler { _, e -> crash.complete(e) }
        )
        val repository = newRepository(externalScope = appScope)
        profileRepository.removeProfile()

        assertThrows<IllegalStateException> { repository.exportBackup(document().first) }
        assertTrue(crash.await() is IllegalStateException)
        appScope.cancel()
    }

    @Test
    fun exportBackup_finishesEvenIfItsCallerIsCancelled() = runTest {
        val (destination, file) = document()
        // Holds the export until the test runs it, after the caller is cancelled.
        val repository = newRepository(
            ioDispatcher = StandardTestDispatcher(testScheduler),
            externalScope = backgroundScope
        )

        val caller = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.exportBackup(destination)
        }
        caller.cancel()
        advanceUntilIdle()

        assertEquals(BackupReadResult.Valid(storedBackup()), readExport(file))
    }

    @Test
    fun readBackup_ofAnExport_isValid() = runTest {
        val backup = storedBackup()

        val read = repository.readBackup(document(encodeBackup(backup)).first)

        assertEquals(BackupReadResult.Valid(backup), read)
    }

    // An editor can add one when it saves the file.
    @Test
    fun readBackup_ofAnExportWithAByteOrderMark_isValid() = runTest {
        val backup = storedBackup()

        val read = repository.readBackup(document("\uFEFF" + encodeBackup(backup)).first)

        assertEquals(BackupReadResult.Valid(backup), read)
    }

    @Test
    fun readBackup_ofAnExportWithABadgeTheCatalogDoesntHave_isNewerFormat() = runTest {
        val archery = BadgeProgressDetails(start.progress("archery"), emptyList(), emptyList())
        val newer = Backup(profile, listOf(archery))

        val read = repository.readBackup(document(encodeBackup(newer)).first)

        assertEquals(BackupReadResult.NewerFormat, read)
    }

    @Test
    fun readBackup_whenTheCatalogCantBeRead_throwsIOException() = runTest {
        val (source, _) = document(encodeBackup(storedBackup()))
        catalogRepository.failLoads = true

        assertThrows<IOException> { repository.readBackup(source) }
    }

    @Test
    fun readBackup_ofANewerExport_isNewerFormat() = runTest {
        val read = repository.readBackup(document("""{ "formatVersion": 3 }""").first)

        assertEquals(BackupReadResult.NewerFormat, read)
    }

    @Test
    fun readBackup_ofAFileThatIsntAnExport_isInvalid() = runTest {
        val files = listOf(
            "Notes from the campout".encodeToByteArray(),
            // The start of a PNG image, which isn't text.
            byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        )
        for (bytes in files) {
            assertEquals(BackupReadResult.Invalid, repository.readBackup(document(bytes).first))
        }
    }

    // A file too large to be an export isn't read in full, so it can't use up the app's memory.
    @Test
    fun readBackup_ofAFileLargerThanTheLimit_isInvalid() = runTest {
        val export = encodeBackup(storedBackup())

        // JSON allows spaces after the export.
        fun padded(size: Int) = export + " ".repeat(size - export.length)

        val atTheLimit = repository.readBackup(
            document(padded(JsonBackupRepository.MAX_FILE_SIZE)).first
        )
        val overTheLimit = repository.readBackup(
            document(padded(JsonBackupRepository.MAX_FILE_SIZE + 1)).first
        )

        assertEquals(BackupReadResult.Valid(storedBackup()), atTheLimit)
        assertEquals(BackupReadResult.Invalid, overTheLimit)
    }

    // Another app's refusal isn't a mistake in BlueCard, so the scout sees that it failed
    // rather than the app closing.
    @Test
    fun readBackup_whenTheDocumentsProviderRefuses_throwsIOException() = runTest {
        val (source, _) = document(encodeBackup(storedBackup()))
        documentsProvider.refuseOpening = true

        assertThrows<IOException> { repository.readBackup(source) }
    }

    @Test
    fun readBackup_whenTheFileIsGone_throwsIOException() = runTest {
        val gone = Uri.fromFile(File(folder.root, "Deleted export.json"))

        assertThrows<IOException> { repository.readBackup(gone) }
    }

    @Test
    fun importBackup_replacesTheProfileAndAllProgress() = runTest {
        val imported = Backup(
            Profile("Sam Scout", "Crew 7"),
            listOf(
                BadgeProgressDetails(
                    start.progress("hiking").copy(counselor = Counselor("Pat")),
                    emptyList(),
                    emptyList()
                )
            )
        )

        repository.importBackup(imported)

        assertEquals(imported, storedBackup())
    }

    @Test
    fun importBackup_whenProgressCantBeSaved_throwsIOException_andChangesNothing() = runTest {
        val before = storedBackup()
        progressRepository.failSaves = true

        assertThrows<IOException> {
            repository.importBackup(Backup(Profile("Sam Scout", "Crew 7"), emptyList()))
        }
        assertEquals(before, storedBackup())
    }

    @Test
    fun importBackup_finishesEvenIfItsCallerIsCancelled() = runTest {
        val saving = CompletableDeferred<Unit>()
        // Holds the profile's save, after the progress is replaced, until the caller is gone.
        val slowProfileRepository = object : ProfileRepository by profileRepository {
            override suspend fun saveProfile(profile: Profile) {
                saving.await()
                profileRepository.saveProfile(profile)
            }
        }
        val repository = newRepository(profileRepository = slowProfileRepository)
        val imported = Backup(Profile("Sam Scout", "Crew 7"), emptyList())

        val caller = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.importBackup(imported)
        }
        caller.cancel()
        saving.complete(Unit)
        advanceUntilIdle()

        assertEquals(imported, storedBackup())
    }

    @Test
    fun mergeBackup_addsUnstartedBadges_replacesThoseChosen_andKeepsTheRest() = runTest {
        val before = storedBackup()
        fun fromFile(id: String) = BadgeProgressDetails(
            start.progress(id).copy(counselor = Counselor("Pat")),
            emptyList(),
            emptyList()
        )
        val file = Backup(
            Profile("Sam Scout", "Crew 7"),
            listOf(fromFile("camping"), fromFile("hiking"), fromFile("swimming"))
        )

        repository.mergeBackup(file, fromFile = setOf("swimming"), profileFromFile = false)

        val camping = before.progress.single { it.badge.badgeId == "camping" }
        assertEquals(
            Backup(profile, listOf(camping, fromFile("hiking"), fromFile("swimming"))),
            storedBackup()
        )
    }

    @Test
    fun mergeBackup_withTheProfileFromTheFile_replacesTheProfile() = runTest {
        val before = storedBackup()

        repository.mergeBackup(
            Backup(Profile("Sam Scout", "Crew 7"), emptyList()),
            fromFile = emptySet(),
            profileFromFile = true
        )

        assertEquals(before.copy(profile = Profile("Sam Scout", "Crew 7")), storedBackup())
    }

    // The issue's acceptance test, through the Room database the app stores progress in.
    @Test
    fun exportThenImport_onAClearedApp_restoresTheSameData() = runTest {
        val exporting = roomProgressRepository()
        exporting.markRequirementCompleted("camping", "4b", day, start)
        exporting.markRequirementCompleted("camping", "5", null, start)
        exporting.setRequirementSignOffAndComment("camping", "6", null, "Next trip.", start)
        exporting.setCounselor(
            "camping",
            Counselor("Pat Lee", "555-0100", "pat@example.com"),
            start
        )
        for (nights in listOf("2", "1", "3")) {
            exporting.addTrackerEntry("camping", "9a", null, mapOf("nights" to nights), day, start)
        }
        exporting.addTrackerEntry("camping", "9b", 2, mapOf("place" to "Lake"), day, start)
        exporting.setCompletedOnPriorDate("swimming", day, start)
        val (export, _) = document()
        newRepository(progressRepository = exporting).exportBackup(export)

        // A phone with the app newly installed, or its data cleared: no profile, no progress.
        val clearedProfile = FakeProfileRepository()
        val importing = roomProgressRepository()
        val cleared = newRepository(clearedProfile, importing)
        val read = cleared.readBackup(export) as BackupReadResult.Valid
        cleared.importBackup(read.backup)

        assertEquals(profile, clearedProfile.observeProfile().first())
        assertEquals(withoutEntryIds(exporting), withoutEntryIds(importing))
        // Each log keeps its order.
        val log = importing.observeProgress("camping").first()!!.trackerEntries
            .filter { it.requirementNumber == "9a" }.sortedBy { it.id }
        assertEquals(listOf("2", "1", "3"), log.map { it.values["nights"] })
    }

    @Test
    fun exportThenImport_withRankProgress_restoresIt() = runTest {
        val exporting = roomProgressRepository()
        exporting.markRequirementCompleted("tenderfoot", "1a", day, start)
        val (export, _) = document()
        newRepository(progressRepository = exporting).exportBackup(export)

        val importing = roomProgressRepository()
        val cleared = newRepository(FakeProfileRepository(), importing)
        val read = cleared.readBackup(export) as BackupReadResult.Valid
        cleared.importBackup(read.backup)

        assertEquals(withoutEntryIds(exporting), withoutEntryIds(importing))
    }

    private fun roomProgressRepository(): RoomProgressRepository {
        val database = Room.inMemoryDatabaseBuilder(context, BlueCardDatabase::class.java)
            .build()
            .also { databases += it }
        return RoomProgressRepository(database, roomScope)
    }

    /**
     * All of [repository]'s progress, without tracker entry IDs, which an import gives anew,
     * with each badge's entries in the order they were added and its requirements in order.
     */
    private suspend fun withoutEntryIds(repository: ProgressRepository) =
        repository.observeAllProgress().first().map { details ->
            details.copy(
                requirements = details.requirements.sortedBy { it.requirementNumber },
                trackerEntries = details.trackerEntries.sortedBy { it.id }.map { it.copy(id = 0) }
            )
        }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue("Expected ${T::class.simpleName}, got $error", error is T)
    }
}
