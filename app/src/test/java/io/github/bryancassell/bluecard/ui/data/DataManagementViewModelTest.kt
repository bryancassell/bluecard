package io.github.bryancassell.bluecard.ui.data

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.backup.Backup
import io.github.bryancassell.bluecard.data.backup.BackupReadResult
import io.github.bryancassell.bluecard.data.backup.FakeBackupRepository
import io.github.bryancassell.bluecard.data.backup.FakeBackupRepository.Merge
import io.github.bryancassell.bluecard.data.catalog.FakeCatalogRepository
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Rank
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.profile.FakeProfileRepository
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.progress.BadgeProgress
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.BadgeStart
import io.github.bryancassell.bluecard.data.progress.FakeProgressRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import io.github.bryancassell.bluecard.testing.FakeClock
import io.github.bryancassell.bluecard.testing.MainDispatcherRule
import io.github.bryancassell.bluecard.ui.data.DataManagementMessage.Kind
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Robolectric, because the failure tests reach android.util.Log, which throws in plain local
// tests, and Uri is Android's (see ARCHITECTURE.md, ViewModel tests).
@RunWith(AndroidJUnit4::class)
class DataManagementViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 10, 1)
    private val clock = FakeClock(today.atTime(12, 0).toInstant(ZoneOffset.UTC))

    private val version = LocalDate.of(2026, 1, 1)
    private val badgeStart = BadgeStart(version, LocalDate.of(2026, 3, 1))

    private val backupRepository = FakeBackupRepository()
    private val catalogRepository = FakeCatalogRepository(
        badges = listOf(
            MeritBadge(
                id = "camping",
                name = "Camping",
                summary = "Our summary of Camping.",
                officialUrl = "https://www.scouting.org/merit-badges/camping/",
                requirementVersions = listOf(
                    RequirementsVersion(version, listOf(Requirement("1", "First.")))
                )
            )
        ),
        ranks = listOf(
            Rank(
                id = "scout",
                name = "Scout",
                summary = "Our summary of Scout.",
                officialUrl = "https://www.scouting.org/scout.pdf",
                requirementVersions = listOf(
                    RequirementsVersion(version, listOf(Requirement("1", "First.")))
                )
            )
        )
    )
    private val profile = Profile("Sam Scout", "Crew 7")
    private val profileRepository = FakeProfileRepository(profile)
    private val progressRepository = FakeProgressRepository()

    private val destination = Uri.parse("content://documents/document/new-export")
    private val export = Uri.parse("content://documents/document/export")
    private val backup = Backup(profile, emptyList())

    /**
     * Created in the test, after MainDispatcherRule has replaced the Main dispatcher that
     * viewModelScope uses. Its uiState is collected, as the screen does, so WhileSubscribed
     * starts it. From the coroutines testing guide:
     * https://developer.android.com/kotlin/coroutines/test#statein
     */
    private fun TestScope.viewModel(progress: ProgressRepository = progressRepository) =
        DataManagementViewModel(
            backupRepository,
            catalogRepository,
            profileRepository,
            progress,
            clock
        ).also { viewModel ->
            backgroundScope.launch(mainDispatcherRule.testDispatcher) {
                viewModel.uiState.collect {}
            }
        }

    private fun DataManagementViewModel.messageKind() = uiState.value.message?.kind

    /** A view model that has read [export], an export of [file] the scout chose to import. */
    private fun TestScope.viewModelWithFileToImport(file: Backup = backup) = viewModel().apply {
        backupRepository.files[export] = BackupReadResult.Valid(file)
        read(export)
    }

    /**
     * An export with the profile [named] and Camping started, with its requirement completed,
     * which differs from the phone's Camping once the phone has it started.
     */
    private fun fileWithCamping(named: String = profile.name) = Backup(
        Profile(named, profile.unitNumber),
        listOf(
            BadgeProgressDetails(
                BadgeProgress("camping", version, badgeStart.startedDate),
                listOf(RequirementProgress("camping", "1", completed = true)),
                emptyList()
            )
        )
    )

    @Test
    fun uiState_startsWithNothingUnderWay() = runTest {
        assertEquals(DataManagementUiState(), viewModel().uiState.value)
    }

    // So the name suggested for an export has the day it's made, even past midnight.
    @Test
    fun today_isFromTheClock_whenAsked() = runTest {
        val viewModel = viewModel()
        assertEquals(today, viewModel.today())

        clock.now = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)

        assertEquals(today.plusDays(1), viewModel.today())
    }

    @Test
    fun export_savesTheExportWhereTheScoutChose() = runTest {
        val viewModel = viewModel()

        viewModel.export(destination)

        assertEquals(listOf(destination), backupRepository.exported)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun export_isWorkingUntilItsDone() = runTest {
        val viewModel = viewModel()
        val exporting = CompletableDeferred<Unit>()
        backupRepository.working = exporting

        viewModel.export(destination)
        assertTrue(viewModel.uiState.value.working)
        exporting.complete(Unit)

        assertFalse(viewModel.uiState.value.working)
    }

    // As when a second tap opens a second file picker before the first covers BlueCard.
    @Test
    fun working_whileTwoOverlap_lastsUntilBothAreDone() = runTest {
        val viewModel = viewModel()
        val exporting = CompletableDeferred<Unit>()
        val reading = CompletableDeferred<Unit>()
        backupRepository.working = exporting
        viewModel.export(destination)
        backupRepository.working = reading
        viewModel.read(export)

        exporting.complete(Unit)
        assertTrue(viewModel.uiState.value.working)
        reading.complete(Unit)

        assertFalse(viewModel.uiState.value.working)
    }

    @Test
    fun export_whenItCantBeSaved_saysSo_untilItsShown() = runTest {
        val viewModel = viewModel()
        backupRepository.failSaves = true

        viewModel.export(destination)

        assertEquals(Kind.ExportFailed, viewModel.messageKind())
        assertFalse(viewModel.uiState.value.working)
        viewModel.onMessageShown(viewModel.uiState.value.message!!)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun read_ofAnExport_asksTheScoutWhetherToMergeOrReplace() = runTest {
        val viewModel = viewModelWithFileToImport()

        assertEquals(backup, viewModel.uiState.value.backupToImport)
        assertEquals(emptyList<Backup>(), backupRepository.imported)
    }

    @Test
    fun read_isWorkingUntilTheFileIsRead() = runTest {
        val viewModel = viewModel()
        val reading = CompletableDeferred<Unit>()
        backupRepository.working = reading
        backupRepository.files[export] = BackupReadResult.Valid(backup)

        viewModel.read(export)
        assertTrue(viewModel.uiState.value.working)
        assertNull(viewModel.uiState.value.backupToImport)
        reading.complete(Unit)

        assertFalse(viewModel.uiState.value.working)
        assertEquals(backup, viewModel.uiState.value.backupToImport)
    }

    @Test
    fun read_ofAFileThatIsntAnExport_saysSo() = runTest {
        val viewModel = viewModel()

        viewModel.read(Uri.parse("content://documents/document/photo"))

        assertEquals(Kind.Invalid, viewModel.messageKind())
        assertNull(viewModel.uiState.value.backupToImport)
    }

    @Test
    fun read_ofANewerExport_saysTheAppNeedsUpdating() = runTest {
        val viewModel = viewModel()
        backupRepository.files[export] = BackupReadResult.NewerFormat

        viewModel.read(export)

        assertEquals(Kind.NewerFormat, viewModel.messageKind())
        assertNull(viewModel.uiState.value.backupToImport)
    }

    @Test
    fun read_whenTheFileCantBeRead_saysSo() = runTest {
        val viewModel = viewModel()
        backupRepository.failReads = true

        viewModel.read(export)

        assertEquals(Kind.ReadFailed, viewModel.messageKind())
        assertFalse(viewModel.uiState.value.working)
    }

    @Test
    fun replace_replacesTheScoutsDataWithTheFile_andSaysSo() = runTest {
        val viewModel = viewModelWithFileToImport()

        viewModel.replace()

        assertEquals(listOf(backup), backupRepository.imported)
        assertNull(viewModel.uiState.value.backupToImport)
        assertEquals(Kind.Imported, viewModel.messageKind())
    }

    @Test
    fun replace_isWorkingUntilItsDone() = runTest {
        val viewModel = viewModelWithFileToImport()
        val importing = CompletableDeferred<Unit>()
        backupRepository.working = importing

        viewModel.replace()
        // The dialog closes as the import starts.
        assertNull(viewModel.uiState.value.backupToImport)
        assertTrue(viewModel.uiState.value.working)
        importing.complete(Unit)

        assertFalse(viewModel.uiState.value.working)
    }

    // A double tap on Replace all.
    @Test
    fun replace_twice_importsOnce() = runTest {
        val viewModel = viewModelWithFileToImport()

        viewModel.replace()
        viewModel.replace()

        assertEquals(listOf(backup), backupRepository.imported)
    }

    @Test
    fun replace_whenItCantBeSaved_saysSo() = runTest {
        val viewModel = viewModelWithFileToImport()
        backupRepository.failSaves = true

        viewModel.replace()

        assertEquals(Kind.ImportFailed, viewModel.messageKind())
        assertFalse(viewModel.uiState.value.working)
    }

    @Test
    fun cancelImport_importsNothing() = runTest {
        val viewModel = viewModelWithFileToImport()

        viewModel.cancelImport()
        viewModel.replace()

        assertNull(viewModel.uiState.value.backupToImport)
        assertEquals(emptyList<Backup>(), backupRepository.imported)
    }

    // Twice too, as a double tap on Merge.
    @Test
    fun merge_withNothingToChoose_mergesStraightAway_andSaysSo() = runTest {
        val file = fileWithCamping()
        val viewModel = viewModelWithFileToImport(file)

        viewModel.merge()
        viewModel.merge()

        assertEquals(
            listOf(Merge(file, emptySet(), profileFromFile = false)),
            backupRepository.merged
        )
        assertNull(viewModel.uiState.value.backupToImport)
        assertNull(viewModel.uiState.value.mergeChoices)
        assertEquals(Kind.Merged, viewModel.messageKind())
    }

    @Test
    fun merge_whereThePhoneAndTheFileDiffer_asksWhichToKeep_startingWithThePhones() = runTest {
        progressRepository.startBadge("camping", version, badgeStart.startedDate)
        val file = fileWithCamping(named = "Sam Lee")
        val viewModel = viewModelWithFileToImport(file)

        viewModel.merge()

        val choices = viewModel.uiState.value.mergeChoices!!
        assertEquals(ProfileChoice(profile, file.profile), choices.profile)
        assertEquals(
            listOf(
                AdvancementChoice(
                    "camping",
                    "Camping",
                    isRank = false,
                    phone = ProgressSummary(done = false, fractionDone = 0f),
                    file = ProgressSummary(done = true)
                )
            ),
            choices.advancements
        )
        assertEquals(file, choices.sources.file)
        assertEquals(profile, choices.sources.phone.profile)
        assertNull(viewModel.uiState.value.backupToImport)
        assertEquals(emptyList<Merge>(), backupRepository.merged)
    }

    @Test
    fun merge_whenTheScoutsDataCantBeRead_saysSo_andMergesNothing() = runTest {
        val viewModel = viewModelWithFileToImport(fileWithCamping())
        progressRepository.failLoads = true

        viewModel.merge()

        assertEquals(Kind.ImportFailed, viewModel.messageKind())
        assertNull(viewModel.uiState.value.mergeChoices)
        assertEquals(emptyList<Merge>(), backupRepository.merged)
    }

    /**
     * A view model asking which to keep, the phone's or the [file]'s, for Camping, which the
     * phone has started, and for the profile if they differ.
     */
    private suspend fun TestScope.viewModelChoosing(file: Backup): DataManagementViewModel {
        progressRepository.startBadge("camping", version, badgeStart.startedDate)
        return viewModelWithFileToImport(file).apply { merge() }
    }

    @Test
    fun chooseProfileAndProgress_changeWhichToKeep() = runTest {
        val viewModel = viewModelChoosing(fileWithCamping(named = "Sam Lee"))
        fun choices() = viewModel.uiState.value.mergeChoices!!

        viewModel.chooseProfile(fromFile = true)
        viewModel.chooseProgress("camping", fromFile = true)

        assertTrue(choices().profile!!.fromFile)
        assertEquals(setOf("camping"), choices().fromFile)

        viewModel.chooseProfile(fromFile = false)
        viewModel.chooseProgress("camping", fromFile = false)

        assertFalse(choices().profile!!.fromFile)
        assertEquals(emptySet<String>(), choices().fromFile)
    }

    @Test
    fun chooseProgress_namesTheRanksTheMergeWouldUnearn() = runTest {
        progressRepository.markRequirementCompleted("scout", "1", null, badgeStart)
        val file = Backup(
            profile,
            listOf(
                BadgeProgressDetails(
                    BadgeProgress("scout", version, badgeStart.startedDate),
                    emptyList(),
                    emptyList()
                )
            )
        )
        val viewModel = viewModelWithFileToImport(file).apply { merge() }
        fun unearned() = viewModel.uiState.value.mergeChoices!!.unearnedRanks

        viewModel.chooseProgress("scout", fromFile = true)
        assertEquals(listOf("Scout"), unearned())

        viewModel.chooseProgress("scout", fromFile = false)
        assertEquals(emptyList<String>(), unearned())
    }

    // Twice too, as a double tap on Merge.
    @Test
    fun confirmMerge_mergesAsTheScoutChose_andSaysSo() = runTest {
        val file = fileWithCamping(named = "Sam Lee")
        val viewModel = viewModelChoosing(file)
        viewModel.chooseProgress("camping", fromFile = true)

        viewModel.confirmMerge()
        viewModel.confirmMerge()

        assertEquals(
            listOf(Merge(file, setOf("camping"), profileFromFile = false)),
            backupRepository.merged
        )
        assertNull(viewModel.uiState.value.mergeChoices)
        assertEquals(Kind.Merged, viewModel.messageKind())
    }

    @Test
    fun confirmMerge_withTheFilesProfile_replacesTheProfile() = runTest {
        val file = fileWithCamping(named = "Sam Lee")
        val viewModel = viewModelChoosing(file)
        viewModel.chooseProfile(fromFile = true)

        viewModel.confirmMerge()

        // Without Camping, which the scout kept the phone's progress on.
        assertEquals(
            listOf(Merge(Backup(file.profile, emptyList()), emptySet(), profileFromFile = true)),
            backupRepository.merged
        )
    }

    @Test
    fun confirmMerge_isWorkingUntilItsDone() = runTest {
        val viewModel = viewModelChoosing(fileWithCamping(named = "Sam Lee"))
        val merging = CompletableDeferred<Unit>()
        backupRepository.working = merging

        viewModel.confirmMerge()
        // The dialog closes as the merge starts.
        assertNull(viewModel.uiState.value.mergeChoices)
        assertTrue(viewModel.uiState.value.working)
        merging.complete(Unit)

        assertFalse(viewModel.uiState.value.working)
    }

    @Test
    fun confirmMerge_whenItCantBeSaved_saysSo() = runTest {
        val viewModel = viewModelChoosing(fileWithCamping(named = "Sam Lee"))
        backupRepository.failSaves = true

        viewModel.confirmMerge()

        assertEquals(Kind.ImportFailed, viewModel.messageKind())
        assertFalse(viewModel.uiState.value.working)
    }

    @Test
    fun cancelImport_whileChoosing_mergesNothing() = runTest {
        val viewModel = viewModelChoosing(fileWithCamping(named = "Sam Lee"))

        viewModel.cancelImport()
        viewModel.confirmMerge()

        assertNull(viewModel.uiState.value.mergeChoices)
        assertEquals(emptyList<Merge>(), backupRepository.merged)
    }

    @Test
    fun uiState_withNoBadgeStarted_cantClear() = runTest {
        assertFalse(viewModel().uiState.value.canClear)
    }

    @Test
    fun uiState_withABadgeStarted_canClear() = runTest {
        progressRepository.startBadge("camping", today, today)

        assertTrue(viewModel().uiState.value.canClear)
    }

    // Rank progress is stored with the badges', and Clear all clears it too.
    @Test
    fun uiState_withOnlyARankStarted_canClear() = runTest {
        progressRepository.startBadge("scout", today, today)

        assertTrue(viewModel().uiState.value.canClear)
    }

    @Test
    fun uiState_whenProgressCantBeRead_cantClear() = runTest {
        progressRepository.startBadge("camping", today, today)
        progressRepository.failLoads = true

        assertFalse(viewModel().uiState.value.canClear)
    }

    // As each time the screen comes back after more than 5 seconds, such as from the file picker.
    @Test
    fun uiState_whileProgressIsRead_showsWhatsUnderWay() = runTest {
        val reading = object : ProgressRepository by progressRepository {
            override fun observeAllProgress(): Flow<List<BadgeProgressDetails>> =
                flow { awaitCancellation() }
        }
        val viewModel = viewModel(reading)
        backupRepository.working = CompletableDeferred()

        viewModel.export(destination)

        assertTrue(viewModel.uiState.value.working)
        assertFalse(viewModel.uiState.value.canClear)
    }

    @Test
    fun clearAll_clearsEveryBadgesProgress_andSaysSo() = runTest {
        progressRepository.startBadge("camping", today, today)
        progressRepository.startBadge("archery", today, today)
        val viewModel = viewModel()

        viewModel.clearAll()

        assertEquals(
            emptyList<BadgeProgressDetails>(),
            progressRepository.observeAllProgress().first()
        )
        assertEquals(Kind.Cleared, viewModel.messageKind())
        assertFalse(viewModel.uiState.value.canClear)
        assertFalse(viewModel.uiState.value.working)
    }

    @Test
    fun clearAll_whenItCantBeSaved_saysSo_andKeepsTheProgress() = runTest {
        progressRepository.startBadge("camping", today, today)
        val viewModel = viewModel()
        progressRepository.failSaves = true

        viewModel.clearAll()

        assertEquals(Kind.ClearFailed, viewModel.messageKind())
        assertEquals(1, progressRepository.observeAllProgress().first().size)
        assertTrue(viewModel.uiState.value.canClear)
        assertFalse(viewModel.uiState.value.working)
    }

    @Test
    fun onMessageShown_ofAnEarlierMessage_keepsTheOneSinceThen() = runTest {
        val viewModel = viewModel()
        backupRepository.failSaves = true
        viewModel.export(destination)
        val first = viewModel.uiState.value.message!!

        viewModel.export(destination)
        val second = viewModel.uiState.value.message!!
        viewModel.onMessageShown(first)

        // The same kind of message, but a new one, which the screen hasn't shown yet.
        assertEquals(second, viewModel.uiState.value.message)
        assertTrue(first !== second)
    }
}
