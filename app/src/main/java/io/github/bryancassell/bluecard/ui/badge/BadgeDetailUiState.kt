package io.github.bryancassell.bluecard.ui.badge

import android.net.Uri
import io.github.bryancassell.bluecard.data.progress.Counselor
import io.github.bryancassell.bluecard.ui.TaskFailure
import io.github.bryancassell.bluecard.ui.badges.EagleRequirement
import java.time.LocalDate

/** What the Badge detail screen shows. */
sealed interface BadgeDetailUiState {
    /** The catalog is still loading. */
    data object Loading : BadgeDetailUiState

    /** The catalog or progress couldn't be read. */
    data object LoadFailed : BadgeDetailUiState

    data class Ready(
        val name: String,
        val summary: String,
        /** Null for a badge that isn't Eagle-required. */
        val eagle: EagleRequirement?,
        val officialUrl: String,
        /** The top-level requirements of the version the scout works on. */
        val requirements: List<RequirementItem>,
        /** The scout's merit badge counselor, or null if they haven't entered one. */
        val counselor: Counselor? = null,
        /** Whether the badge is complete, so its report can be shared or saved. */
        val completed: Boolean = false,
        /**
         * The date the scout marked the badge completed on, without recording its requirements,
         * or null if they haven't.
         */
        val completedOnPriorDate: LocalDate? = null,
        /**
         * The date the badge was marked completed on before the scout unmarked it on this page,
         * which Mark completed's picker opens at, or null.
         */
        val unmarkedDate: LocalDate? = null,
        /** How much of the badge is done, from 0 to 1, while it's in progress, or null. */
        val fractionDone: Float? = null,
        /** Whether the badge is started, so its progress can be cleared. */
        val canClear: Boolean = false,
        /**
         * The badge's report, once it's ready to share, until the screen has opened the share
         * sheet with it.
         */
        val reportToShare: Uri? = null,
        /** The report couldn't be created or saved, and the scout hasn't been told yet. */
        val reportFailure: TaskFailure? = null,
        /** The badge's progress couldn't be cleared, and the scout hasn't been told yet. */
        val saveFailure: TaskFailure? = null
    ) : BadgeDetailUiState

    /**
     * The catalog doesn't have the badge or its requirements version ([badgeRequirements]).
     * Only a catalog edited during development can cause that.
     */
    data object Unavailable : BadgeDetailUiState
}
