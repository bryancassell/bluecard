package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.ui.theme.BlueCardTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * How the buttons that open a dialog to remove what the scout recorded look: red, like the
 * dialog's confirm button, which semantics can't show. Checked against reference images in
 * `src/test/screenshots`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// The same fixed screen and SDK as RequirementRowScreenshotTest, for the same reasons.
@Config(qualifiers = "w360dp-h640dp-xhdpi", sdk = [36])
class RemovalButtonScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val today = LocalDate.of(2026, 5, 20)

    @Test
    fun clearProgressIsRed() {
        composeTestRule.setContent {
            BlueCardTheme {
                RequirementDetailScreen(
                    uiState = RequirementDetailUiState.Ready(
                        badgeName = "Camping",
                        requirement = RequirementItem(
                            "1",
                            "Plan a campout.",
                            null,
                            completed = true,
                            markedByHand = true
                        ),
                        completedDate = today,
                        children = emptyList(),
                        tracker = null,
                        commentChanged = false,
                        canClear = true,
                        today = today
                    ),
                    comment = TextFieldState(),
                    onOpenRequirement = {},
                    onOpenTrackerEntry = { _, _ -> },
                    onCompletedChange = {},
                    onCompletedDateChange = {},
                    onSaveComment = {},
                    onClear = {},
                    onSaveFailureShown = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Clear progress").performScrollTo().captureRoboImage()
    }

    @Test
    fun deleteIsRed() {
        val columns = listOf(TrackerColumn("activity", "Activity", TrackerColumnType.TEXT))
        composeTestRule.setContent {
            BlueCardTheme {
                TrackerEntryScreen(
                    uiState = TrackerEntryUiState.Ready(
                        badgeName = "Personal Fitness",
                        requirementNumber = "7a",
                        rowTitle = "Session",
                        rowNumber = 2,
                        rowLabel = "session",
                        columns = columns,
                        dates = emptyMap(),
                        canSave = false,
                        hasSavedEntry = true,
                        canDelete = true,
                        today = today
                    ),
                    fields = columns.associate { it.id to TextFieldState("Ran 2 miles") },
                    onDateChange = { _, _ -> },
                    onSave = {},
                    onDelete = {},
                    onClose = {},
                    onSaveFailureShown = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Delete").performScrollTo().captureRoboImage()
    }
}
