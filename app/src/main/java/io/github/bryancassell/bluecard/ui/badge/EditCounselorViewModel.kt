package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.progress.Counselor
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.badgeStart
import io.github.bryancassell.bluecard.ui.StoredTextFields
import io.github.bryancassell.bluecard.ui.TaskFailure
import io.github.bryancassell.bluecard.ui.TaskRunner
import io.github.bryancassell.bluecard.ui.catchLoadFailure
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * A badge's merit badge counselor, in fields the scout edits and then saves. Once saved, the
 * page closes.
 */
@HiltViewModel(assistedFactory = EditCounselorViewModel.Factory::class)
class EditCounselorViewModel @AssistedInject constructor(
    @Assisted private val badgeId: String,
    private val catalogRepository: CatalogRepository,
    private val progressRepository: ProgressRepository,
    private val clock: Clock,
    // Keeps unsaved fields if the system stops the app in the background.
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    // They start as the saved counselor once it loads, or as the unsaved fields the system
    // stopped the app with.
    private val fields = StoredTextFields(savedStateHandle, NAME, PHONE, EMAIL)

    /** The counselor's name field, which the field edits directly. */
    val name = fields[NAME]

    /** The counselor's phone number field. */
    val phone = fields[PHONE]

    /** The counselor's email address field. */
    val email = fields[EMAIL]

    private val saves = TaskRunner(viewModelScope)

    /** Whether the fields have been saved and the page hasn't closed yet. */
    private val saved = MutableStateFlow(false)

    val uiState: StateFlow<EditCounselorUiState> = combine(
        flow { emit(catalogRepository.getBadges()) },
        progressRepository.observeProgress(badgeId),
        // Works the state out again as the scout types.
        snapshotFlow { typed() },
        saved,
        saves.failure
    ) { catalog, progress, _, isSaved, saveFailure ->
        // Unavailable when Badge detail is, which then doesn't open this page. Starting the badge
        // on a save (badgeStart) needs the badge in the catalog too.
        val found = catalog.badgeRequirements(badgeId, progress)
            ?: return@combine EditCounselorUiState.Unavailable
        val stored = progress?.badge?.counselor
        fields.loadOnce {
            mapOf(NAME to stored?.name, PHONE to stored?.phone, EMAIL to stored?.email)
        }
        EditCounselorUiState.Ready(
            badgeName = found.badge.name,
            // Not once saved: the page is closing, though the saved counselor may not be read
            // yet.
            changed = !isSaved && typed().normalized() != stored,
            saved = isSaved,
            saveFailure = saveFailure
        )
    }.catchLoadFailure(EditCounselorUiState.LoadFailed).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        EditCounselorUiState.Loading
    )

    /** What the fields hold. */
    private fun typed() =
        Counselor(name.text.toString(), phone.text.toString(), email.text.toString())

    /**
     * Saves the fields as the badge's counselor, then closes the page. Empty fields are removed,
     * and with every field empty, so is the counselor.
     */
    fun save() {
        val counselor = typed()
        saves.launch {
            progressRepository.setCounselor(
                badgeId,
                counselor,
                catalogRepository.getBadges().badgeStart(badgeId, LocalDate.now(clock))
            )
            // A field changed while it saved stays open to be saved too, rather than being lost.
            if (typed().normalized() == counselor.normalized()) saved.value = true
        }
    }

    /**
     * The page has closed after saving. If it's opened again before this ViewModel is cleared,
     * as a screen reader can while the page is still animating out, it stays open.
     */
    fun onClosed() {
        saved.value = false
    }

    /** The scout has been told about [failure]. */
    fun onSaveFailureShown(failure: TaskFailure) {
        saves.onShown(failure)
    }

    @AssistedFactory
    interface Factory {
        fun create(badgeId: String): EditCounselorViewModel
    }

    private companion object {
        const val NAME = "name"
        const val PHONE = "phone"
        const val EMAIL = "email"
    }
}
