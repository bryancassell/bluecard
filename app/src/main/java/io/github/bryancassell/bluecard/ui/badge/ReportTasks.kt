package io.github.bryancassell.bluecard.ui.badge

import android.net.Uri
import io.github.bryancassell.bluecard.data.report.ReportRepository
import io.github.bryancassell.bluecard.ui.TaskFailure
import io.github.bryancassell.bluecard.ui.TaskRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Badge or rank [advancementId]'s report, for its page's ViewModel: created to share, for the
 * screen to open the share sheet with, or saved where the scout chose. It runs in [scope] with a
 * [TaskRunner] of its own, so a report that fails shows its own message, not a save's.
 */
class ReportTasks(
    scope: CoroutineScope,
    private val reportRepository: ReportRepository,
    private val advancementId: String
) {
    private val runner = TaskRunner(scope)
    private val toShare = MutableStateFlow<Uri?>(null)

    /** The report, once it's ready to share, until the screen has opened the share sheet. */
    val reportToShare: StateFlow<Uri?> = toShare.asStateFlow()

    /** The report couldn't be created or saved, and the scout hasn't been told yet. */
    val failure: StateFlow<TaskFailure?> = runner.failure

    /** The report being created to share, if there is one. */
    private var creating: Job? = null

    /**
     * The page's latest save. A report asked for after it waits for it, so it reads what the
     * scout last did: not the badge or rank from before a clear, as it could in the moment
     * before the page redraws without its report buttons, or from before its date was changed.
     */
    var lastSave: Job? = null

    /**
     * Creates the report to share ([reportToShare]). Does nothing while one is being created, so
     * a double tap shares it once.
     */
    fun share() {
        if (creating?.isActive == true) return
        val save = lastSave
        creating = runner.launch {
            save?.join()
            toShare.value = reportRepository.createReportToShare(advancementId)
        }
    }

    /** The screen has opened the share sheet with the report. */
    fun onShared() {
        toShare.value = null
    }

    /** Saves the report to [destination], a document the scout chose to create. */
    fun save(destination: Uri) {
        val save = lastSave
        runner.launch {
            save?.join()
            reportRepository.saveReport(advancementId, destination)
        }
    }

    /** The scout has been told about [failure]. */
    fun onFailureShown(failure: TaskFailure) {
        runner.onShown(failure)
    }

    /**
     * Drops a report still being created to share, or ready to share, so the share sheet doesn't
     * open with what the scout just cleared.
     */
    fun drop() {
        creating?.cancel()
        toShare.value = null
    }
}
