package io.github.bryancassell.bluecard.ui.data

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.backup.Backup
import io.github.bryancassell.bluecard.data.backup.BackupReadResult
import io.github.bryancassell.bluecard.data.backup.FakeBackupRepository
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.testing.FakeClock
import io.github.bryancassell.bluecard.testing.MainDispatcherRule
import io.github.bryancassell.bluecard.ui.data.DataManagementMessage.Kind
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
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

    private val backupRepository = FakeBackupRepository()

    private val destination = Uri.parse("content://documents/document/new-export")
    private val export = Uri.parse("content://documents/document/export")
    private val backup = Backup(Profile("Sam Scout", "Crew 7"), emptyList())

    // Created in the test, after MainDispatcherRule has replaced the Main dispatcher that
    // viewModelScope uses.
    private fun viewModel() = DataManagementViewModel(backupRepository, clock)

    private fun DataManagementViewModel.messageKind() = uiState.value.message?.kind

    /** A view model that has read [export], an export the scout chose to import. */
    private fun viewModelWithFileToImport() = viewModel().apply {
        backupRepository.files[export] = BackupReadResult.Valid(backup)
        read(export)
    }

    @Test
    fun uiState_startsWithNothingUnderWay() {
        assertEquals(DataManagementUiState(), viewModel().uiState.value)
    }

    // So the name suggested for an export has the day it's made, even past midnight.
    @Test
    fun today_isFromTheClock_whenAsked() {
        val viewModel = viewModel()
        assertEquals(today, viewModel.today())

        clock.now = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)

        assertEquals(today.plusDays(1), viewModel.today())
    }

    @Test
    fun export_savesTheExportWhereTheScoutChose() {
        val viewModel = viewModel()

        viewModel.export(destination)

        assertEquals(listOf(destination), backupRepository.exported)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun export_isWorkingUntilItsDone() {
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
    fun working_whileTwoOverlap_lastsUntilBothAreDone() {
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
    fun export_whenItCantBeSaved_saysSo_untilItsShown() {
        val viewModel = viewModel()
        backupRepository.failSaves = true

        viewModel.export(destination)

        assertEquals(Kind.ExportFailed, viewModel.messageKind())
        assertFalse(viewModel.uiState.value.working)
        viewModel.onMessageShown(viewModel.uiState.value.message!!)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun read_ofAnExport_asksTheScoutToConfirmImportingIt() {
        val viewModel = viewModelWithFileToImport()

        assertEquals(backup, viewModel.uiState.value.backupToImport)
        assertEquals(emptyList<Backup>(), backupRepository.imported)
    }

    @Test
    fun read_isWorkingUntilTheFileIsRead() {
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
    fun read_ofAFileThatIsntAnExport_saysSo() {
        val viewModel = viewModel()

        viewModel.read(Uri.parse("content://documents/document/photo"))

        assertEquals(Kind.Invalid, viewModel.messageKind())
        assertNull(viewModel.uiState.value.backupToImport)
    }

    @Test
    fun read_ofANewerExport_saysTheAppNeedsUpdating() {
        val viewModel = viewModel()
        backupRepository.files[export] = BackupReadResult.NewerFormat

        viewModel.read(export)

        assertEquals(Kind.NewerFormat, viewModel.messageKind())
        assertNull(viewModel.uiState.value.backupToImport)
    }

    @Test
    fun read_whenTheFileCantBeRead_saysSo() {
        val viewModel = viewModel()
        backupRepository.failReads = true

        viewModel.read(export)

        assertEquals(Kind.ReadFailed, viewModel.messageKind())
        assertFalse(viewModel.uiState.value.working)
    }

    @Test
    fun confirmImport_importsTheFile_andSaysSo() {
        val viewModel = viewModelWithFileToImport()

        viewModel.confirmImport()

        assertEquals(listOf(backup), backupRepository.imported)
        assertNull(viewModel.uiState.value.backupToImport)
        assertEquals(Kind.Imported, viewModel.messageKind())
    }

    @Test
    fun confirmImport_isWorkingUntilItsDone() {
        val viewModel = viewModelWithFileToImport()
        val importing = CompletableDeferred<Unit>()
        backupRepository.working = importing

        viewModel.confirmImport()
        // The dialog closes as the import starts.
        assertNull(viewModel.uiState.value.backupToImport)
        assertTrue(viewModel.uiState.value.working)
        importing.complete(Unit)

        assertFalse(viewModel.uiState.value.working)
    }

    // A double tap on Replace.
    @Test
    fun confirmImport_twice_importsOnce() {
        val viewModel = viewModelWithFileToImport()

        viewModel.confirmImport()
        viewModel.confirmImport()

        assertEquals(listOf(backup), backupRepository.imported)
    }

    @Test
    fun confirmImport_whenItCantBeSaved_saysSo() {
        val viewModel = viewModelWithFileToImport()
        backupRepository.failSaves = true

        viewModel.confirmImport()

        assertEquals(Kind.ImportFailed, viewModel.messageKind())
        assertFalse(viewModel.uiState.value.working)
    }

    @Test
    fun cancelImport_importsNothing() {
        val viewModel = viewModelWithFileToImport()

        viewModel.cancelImport()
        viewModel.confirmImport()

        assertNull(viewModel.uiState.value.backupToImport)
        assertEquals(emptyList<Backup>(), backupRepository.imported)
    }

    @Test
    fun onMessageShown_ofAnEarlierMessage_keepsTheOneSinceThen() {
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
