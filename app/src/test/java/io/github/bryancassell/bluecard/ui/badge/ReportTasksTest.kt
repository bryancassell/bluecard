package io.github.bryancassell.bluecard.ui.badge

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.progress.FakeProgressRepository
import io.github.bryancassell.bluecard.data.report.FakeReportRepository
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

// Robolectric, because a failed task logs with android.util.Log, which throws in plain local
// tests, and for Uri.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ReportTasksTest {
    private val progressRepository = FakeProgressRepository().apply {
        runBlocking { startBadge("chess", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1)) }
    }
    private val reportRepository = FakeReportRepository(progressRepository)
    private val chessReport = FakeReportRepository.reportUri("chess")
    private val destination = Uri.parse("content://documents/chess-report.pdf")

    /** Tasks that run straight away, until the test ends, as on the Main dispatcher. */
    private fun TestScope.reportTasks() = ReportTasks(backgroundScope, reportRepository, "chess")

    private fun runReportTest(test: suspend TestScope.() -> Unit) =
        runTest(UnconfinedTestDispatcher()) { test() }

    @Test
    fun share_createsTheReport_forTheScreenToShare_untilItsShared() = runReportTest {
        val tasks = reportTasks()

        tasks.share()

        assertEquals(listOf("chess"), reportRepository.shared)
        assertEquals(chessReport, tasks.reportToShare.value)

        tasks.onShared()

        assertNull(tasks.reportToShare.value)
    }

    // A double tap on Share report would otherwise open the share sheet twice.
    @Test
    fun share_whileTheReportIsBeingCreated_createsItOnce() = runReportTest {
        val tasks = reportTasks()
        val writing = CompletableDeferred<Unit>()
        reportRepository.writing = writing

        tasks.share()
        tasks.share()
        writing.complete(Unit)

        assertEquals(listOf("chess"), reportRepository.shared)

        tasks.onShared()
        tasks.share()

        assertEquals(listOf("chess", "chess"), reportRepository.shared)
    }

    @Test
    fun save_savesTheReportWhereTheScoutChose() = runReportTest {
        val tasks = reportTasks()

        tasks.save(destination)

        assertEquals(listOf("chess" to destination), reportRepository.saved)
        assertNull(tasks.failure.value)
    }

    @Test
    fun aReportThatFails_isKeptUntilTheScoutIsTold() = runReportTest {
        val tasks = reportTasks()
        reportRepository.failSaves = true

        tasks.share()

        val failure = tasks.failure.value
        assertNotNull(failure)
        assertNull(tasks.reportToShare.value)

        tasks.onFailureShown(failure!!)

        assertNull(tasks.failure.value)

        tasks.save(destination)

        assertNotNull(tasks.failure.value)
    }

    // So the report has what the scout just changed, such as a new date.
    @Test
    fun aReportAskedForAfterASave_waitsForIt() = runReportTest {
        val tasks = reportTasks()
        val save = Job()
        tasks.follow(save)

        tasks.share()
        tasks.save(destination)

        assertEquals(emptyList<String>(), reportRepository.shared)
        assertEquals(emptyList<Pair<String, Uri>>(), reportRepository.saved)

        save.complete()

        assertEquals(chessReport, tasks.reportToShare.value)
        assertEquals(listOf("chess" to destination), reportRepository.saved)
    }

    // Otherwise the share sheet would open with what the scout just changed or cleared.
    @Test
    fun follow_dropsAReportBeingCreated() = runReportTest {
        val tasks = reportTasks()
        val writing = CompletableDeferred<Unit>()
        reportRepository.writing = writing
        tasks.share()

        tasks.follow(Job())
        writing.complete(Unit)

        assertNull(tasks.reportToShare.value)
        assertNull(tasks.failure.value)
    }

    @Test
    fun follow_dropsAReportReadyToShare() = runReportTest {
        val tasks = reportTasks()
        tasks.share()

        tasks.follow(Job())

        assertNull(tasks.reportToShare.value)
    }
}
