package io.github.bryancassell.bluecard.ui.rank

import io.github.bryancassell.bluecard.data.progress.RankStatus
import io.github.bryancassell.bluecard.ui.TaskFailure
import io.github.bryancassell.bluecard.ui.badge.RequirementItem
import java.time.LocalDate

/** What the Rank detail screen shows. */
sealed interface RankDetailUiState {
    /** The catalog is still loading. */
    data object Loading : RankDetailUiState

    /** The catalog or progress couldn't be read. */
    data object LoadFailed : RankDetailUiState

    data class Ready(
        val name: String,
        val summary: String,
        val officialUrl: String,
        /** The top-level requirements of the version the scout works on. */
        val requirements: List<RequirementItem>,
        val status: RankStatus,
        /**
         * How much of the rank is done, from 0 to 1, while it's in progress, or once it's started
         * if it isn't earned. Null otherwise.
         */
        val fractionDone: Float? = null,
        /**
         * The date the scout marked the rank earned on, without recording its requirements, or
         * null if they haven't.
         */
        val earnedOnPriorDate: LocalDate? = null,
        /**
         * The name of the rank above this one that counts it as earned, because the scout marked
         * that one earned on a prior date, when nothing else does. Null otherwise.
         */
        val earnedWith: String? = null,
        /**
         * The date the rank was marked earned on before the scout unmarked it on this page, which
         * the date picker opens at, or null.
         */
        val unmarkedDate: LocalDate? = null,
        /** Whether the rank is started, so its progress can be cleared. */
        val canClear: Boolean = false,
        /**
         * The other ranks, in the order they're earned, that count as earned now but wouldn't
         * once this rank's progress is cleared, so the scout is told before clearing: those its
         * mark counts as earned, and those above it earned in order after it.
         */
        val unearnedByClear: List<String> = emptyList(),
        /** A save couldn't be made, and the scout hasn't been told yet. */
        val saveFailure: TaskFailure? = null
    ) : RankDetailUiState

    /**
     * The catalog doesn't have the rank or its requirements version. Only a catalog edited during
     * development can cause that.
     */
    data object Unavailable : RankDetailUiState
}
