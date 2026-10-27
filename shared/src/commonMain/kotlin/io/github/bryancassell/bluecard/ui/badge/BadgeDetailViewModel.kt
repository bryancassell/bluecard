package io.github.bryancassell.bluecard.ui.badge

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.bryancassell.bluecard.data.DocumentUri
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.badgeStart
import io.github.bryancassell.bluecard.data.progress.completion
import io.github.bryancassell.bluecard.data.progress.fractionDoneWhileInProgress
import io.github.bryancassell.bluecard.data.progress.noLongerEarned
import io.github.bryancassell.bluecard.data.progress.status
import io.github.bryancassell.bluecard.data.report.ReportRepository
import io.github.bryancassell.bluecard.di.WarningLog
import io.github.bryancassell.bluecard.di.ZonedClock
import io.github.bryancassell.bluecard.text.PhoneNumberFormat
import io.github.bryancassell.bluecard.text.TextRules
import io.github.bryancassell.bluecard.ui.TaskFailure
import io.github.bryancassell.bluecard.ui.TaskRunner
import io.github.bryancassell.bluecard.ui.badges.eagleGroups
import io.github.bryancassell.bluecard.ui.badges.eagleRequirement
import io.github.bryancassell.bluecard.ui.catchLoadFailure
import io.github.bryancassell.bluecard.ui.dateFromEpochDay
import io.github.bryancassell.bluecard.ui.navigation.BadgeDetail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import org.koin.core.annotation.InjectedParam

/**
 * One badge from the catalog, with the scout's progress on its requirements, and its report
 * once it's complete. While it isn't complete, the scout can mark it completed on a prior date.
 * Once it's started, its progress can be cleared.
 */
class BadgeDetailViewModel(
    @InjectedParam key: BadgeDetail,
    private val catalogRepository: CatalogRepository,
    private val progressRepository: ProgressRepository,
    reportRepository: ReportRepository,
    private val clock: ZonedClock,
    textRules: TextRules,
    phoneNumberFormat: PhoneNumberFormat,
    warningLog: WarningLog,
    // Keeps the date to open Mark completed's picker at if the system stops the app in the
    // background.
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val badgeId = key.badgeId

    private val report = ReportTasks(viewModelScope, reportRepository, badgeId, warningLog)
    private val saves = TaskRunner(viewModelScope, warningLog)

    /**
     * The counselor's phone number, as typed and formatted. Formatted apart from the page, and
     * only when the number changes, so the first number, which reads phone number data from disk,
     * is formatted while the catalog loads.
     */
    private val counselorPhone: Flow<FormattedPhone?> = progressRepository.observeProgress(badgeId)
        .map { it?.badge?.counselor?.phone }
        .distinctUntilChanged()
        .map { typed -> typed?.let { FormattedPhone(it, phoneNumberFormat.format(it)) } }

    /**
     * The page as worked out from the catalog and progress, before [uiState] adds the date just
     * unmarked and the report.
     */
    private val page: Flow<BadgeDetailUiState> = combine(
        // What depends only on the catalog is worked out once, not on every progress change.
        flow {
            val catalog = catalogRepository.getBadges()
            emit(Triple(catalog, catalog.eagleGroups(textRules), catalogRepository.getRanks()))
        },
        // Every badge's and rank's, since the badges the scout has completed decide which ranks
        // clearing this one would un-earn.
        progressRepository.observeAllProgress(),
        saves.failure,
        counselorPhone
    ) { (catalog, eagleGroups, ranks), allProgress, saveFailure, phone ->
        val progressById = allProgress.associateBy { it.badge.badgeId }
        val progress = progressById[badgeId]
        val found = catalog.advancementRequirements(badgeId, progress)
            ?: return@combine BadgeDetailUiState.Unavailable
        val badge = found.advancement
        val completion = progress?.completion(found.version)
        val counselor = progress?.badge?.counselor
        BadgeDetailUiState.Ready(
            name = badge.name,
            summary = badge.summary,
            eagle = badge.eagleRequirement(eagleGroups),
            officialUrl = badge.officialUrl,
            requirements = found.version.requirements.map(found::item),
            counselor = counselor,
            // Just after the number changes, the format can still be the old number's.
            counselorPhone = phone?.takeIf { it.typed == counselor?.phone }?.formatted,
            status = badge.status(progress),
            completedOnPriorDate = progress?.badge?.completedOnPriorDate,
            completedOn = completion?.date,
            fractionDone = badge.fractionDoneWhileInProgress(progress),
            canClear = progress != null,
            unearnedByClear = if (progress == null) {
                emptyList()
            } else {
                ranks.noLongerEarned(catalog, progressById, progressById - badgeId)
                    .map { it.name }
            },
            saveFailure = saveFailure
        )
    }

    val uiState: StateFlow<BadgeDetailUiState> = combine(
        page,
        savedStateHandle.getStateFlow<Any?>(UNMARKED_DATE, null),
        report.reportToShare,
        report.failure
    ) { state, unmarked, reportToShare, reportFailure ->
        // Combined after, so remembering a date or sharing a report doesn't work out again which
        // ranks clearing the badge would un-earn.
        if (state is BadgeDetailUiState.Ready) {
            state.copy(
                unmarkedDate = dateFromEpochDay(unmarked),
                reportToShare = reportToShare,
                reportFailure = reportFailure
            )
        } else {
            state
        }
    }.catchLoadFailure(BadgeDetailUiState.LoadFailed, warningLog)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BadgeDetailUiState.Loading)

    /**
     * Creates the badge's report, for the screen to open the share sheet with
     * ([BadgeDetailUiState.Ready.reportToShare]). Does nothing while one is being created, so a
     * double tap shares it once.
     */
    fun shareReport() {
        report.share()
    }

    /** The screen has opened the share sheet with the report. */
    fun onReportShared() {
        report.onShared()
    }

    /** Saves the badge's report to [destination], a document the scout chose to create. */
    fun saveReport(destination: DocumentUri) {
        report.save(destination)
    }

    /** The scout has been told about [failure]. */
    fun onReportFailureShown(failure: TaskFailure) {
        report.onFailureShown(failure)
    }

    /**
     * Clears everything recorded for the badge, including its counselor, so it isn't started
     * anymore. The page stays open, which shows the scout what the clear did, as clearing a
     * requirement does, and then shows the requirements of the newest version. A report still
     * being created to share is dropped, so the share sheet doesn't open with what was cleared.
     */
    fun clear() {
        // Forgotten even if the clear fails, as the scout meant it to be.
        savedStateHandle[UNMARKED_DATE] = null
        report.follow(saves.launch { progressRepository.clearBadge(badgeId) })
    }

    /**
     * Marks the badge completed on [date] without recording its requirements, as for a badge
     * earned before the scout used the app, or changes the date it's marked with. A badge that
     * isn't started is started, as when anything is recorded. The date isn't checked against its
     * requirements' dates: completion dates are informational, not a formal requirement, and a
     * scout may record a requirement's details after the badge was earned (#41).
     */
    fun markCompleted(date: LocalDate) {
        report.follow(
            saves.launch {
                progressRepository.setCompletedOnPriorDate(
                    badgeId,
                    date,
                    catalogRepository.getBadges().badgeStart(badgeId, today())
                )
                savedStateHandle[UNMARKED_DATE] = null
            }
        )
    }

    /**
     * Undoes [markCompleted]. What's recorded for the badge stays. The page remembers the date it
     * showed ([BadgeDetailUiState.Ready.unmarkedDate]), so unlike clearing it asks nothing first:
     * asking would slow a one-tap change.
     */
    fun unmarkCompleted() {
        val shown = (uiState.value as? BadgeDetailUiState.Ready)?.completedOnPriorDate
        if (shown != null) savedStateHandle[UNMARKED_DATE] = shown.toEpochDays()
        report.follow(saves.launch { progressRepository.removeCompletedOnPriorDate(badgeId) })
    }

    /** The scout has been told that a save failed ([failure]). */
    fun onSaveFailureShown(failure: TaskFailure) {
        saves.onShown(failure)
    }

    /** The latest date the badge can be marked completed on, read from the clock each time. */
    fun today(): LocalDate = clock.today()

    private companion object {
        /**
         * The epoch day of the date the badge was marked completed on before the scout unmarked
         * it on this page, or null. Mark completed's picker opens at it, so a mistaken Unmark
         * loses nothing. Forgotten once the badge is marked again or cleared, or when the page
         * closes. It's set to null rather than removed, which would stop uiState following it.
         */
        const val UNMARKED_DATE = "unmarkedDate"
    }
}

/** A phone number as the scout [typed] it, and [formatted] to show. */
private data class FormattedPhone(val typed: String, val formatted: String)
