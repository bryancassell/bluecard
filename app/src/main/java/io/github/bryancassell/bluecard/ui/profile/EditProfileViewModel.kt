package io.github.bryancassell.bluecard.ui.profile

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.ui.SaveFailure
import io.github.bryancassell.bluecard.ui.SaveRunner
import io.github.bryancassell.bluecard.ui.StoredTextFields
import io.github.bryancassell.bluecard.ui.catchLoadFailure
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The scout's name and unit number, in fields the scout edits and then saves. Once saved, the
 * page closes.
 */
@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    // Keeps unsaved fields if the system stops the app in the background.
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    // They start as the saved profile once it loads, or as the unsaved fields the system
    // stopped the app with.
    private val fields = StoredTextFields(savedStateHandle, NAME, UNIT_NUMBER)

    /** The name field's text, which the field edits directly. */
    val name = fields[NAME]

    /** The unit number field's text. */
    val unitNumber = fields[UNIT_NUMBER]

    private val saves = SaveRunner(viewModelScope)

    /** Whether the fields have been saved and the page hasn't closed yet. */
    private val saved = MutableStateFlow(false)

    val uiState: StateFlow<EditProfileUiState> = combine(
        profileRepository.observeProfile(),
        // Works the state out again as the scout types.
        snapshotFlow { typed() },
        saved,
        saves.failure
    ) { stored, _, isSaved, saveFailure ->
        fields.loadOnce { mapOf(NAME to stored?.name, UNIT_NUMBER to stored?.unitNumber) }
        val typed = typed()
        EditProfileUiState.Ready(
            canSave = typed.isComplete() && typed != stored,
            saved = isSaved,
            saveFailure = saveFailure
        )
    }.catchLoadFailure(EditProfileUiState.LoadFailed).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        EditProfileUiState.Loading
    )

    /** What the fields hold, trimmed, as Onboarding saves them. */
    private fun typed() = Profile(name.text.trim().toString(), unitNumber.text.trim().toString())

    private fun Profile.isComplete() = name.isNotEmpty() && unitNumber.isNotEmpty()

    /**
     * Saves the fields as the scout's name and unit number, then closes the page. Does nothing if
     * either is blank. Checks the fields' text directly, so it can't save a field emptied since
     * [uiState] last changed.
     */
    fun save() {
        val profile = typed()
        if (!profile.isComplete()) return
        saves.launch {
            profileRepository.saveProfile(profile)
            // A field changed while it saved stays open to be saved too, rather than being lost.
            if (typed() == profile) saved.value = true
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
    fun onSaveFailureShown(failure: SaveFailure) {
        saves.onShown(failure)
    }

    private companion object {
        const val NAME = "name"
        const val UNIT_NUMBER = "unit_number"
    }
}
