package io.github.bryancassell.bluecard.ui.data

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.backup.Backup
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.testing.isPoliteLiveRegion
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Merge dialog shown again after the phone rotates. The activity is recreated here, as on a
 * phone, since the line's text is retained, which StateRestorationTester doesn't keep.
 */
@RunWith(AndroidJUnit4::class)
class MergeDialogRotationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val backup = Backup(Profile("Sam Scout", "Crew 7"), emptyList())

    // Tests change it after showMergeDialog(), as the scout's choices would.
    private var choices by mutableStateOf(
        MergeChoices(
            MergeSources(emptyList(), emptyList(), backup, backup),
            profile = null,
            advancements = emptyList(),
            unearnedRanks = listOf("Scout")
        )
    )

    // Called again after the activity is recreated, as MainActivity sets its content each time.
    private fun showMergeDialog() {
        composeTestRule.activityRule.scenario.onActivity {
            it.setContent {
                MergeDialog(
                    choices,
                    onChooseProfile = {},
                    onChooseProgress = { _, _ -> },
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }
    }

    private val isLiveRegion = SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)

    // Read out again, it held back what TalkBack says for the dialog by about 2 seconds (#281).
    @Test
    fun ranksTheMergeWouldUnearn_arentReadOutAgain_afterThePhoneRotates() {
        showMergeDialog()
        composeTestRule.onNodeWithText("Scout will no longer count as earned.")
            .assert(isPoliteLiveRegion)

        composeTestRule.activityRule.scenario.recreate()
        showMergeDialog()

        composeTestRule.onNodeWithText("Scout will no longer count as earned.")
            .assertIsDisplayed()
            .assert(!isLiveRegion)
        // As the scout chooses again.
        choices = choices.copy(unearnedRanks = listOf("Scout", "Life"))
        composeTestRule.onNodeWithText("Scout and Life will no longer count as earned.")
            .assert(isPoliteLiveRegion)
    }
}
