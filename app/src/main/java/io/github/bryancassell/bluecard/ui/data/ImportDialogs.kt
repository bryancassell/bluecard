package io.github.bryancassell.bluecard.ui.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.ui.AnnouncedText
import io.github.bryancassell.bluecard.ui.ConfirmDialog
import io.github.bryancassell.bluecard.ui.badge.rememberCompletionDateFormatter
import io.github.bryancassell.bluecard.ui.badges.percentDoneDescription
import io.github.bryancassell.bluecard.ui.badges.rememberBadgeNameListFormatter
import io.github.bryancassell.bluecard.ui.readAsOneLabel
import io.github.bryancassell.bluecard.ui.removalButtonColors
import io.github.bryancassell.bluecard.ui.typedText
import kotlinx.coroutines.delay

/**
 * Asks the scout, once a file to import is checked, whether to merge it with their data
 * ([onMerge]) or replace all of it ([onReplace]). Replace all is red, as it removes what they
 * recorded. Cancel, Back and a tap outside call [onDismiss].
 */
@Composable
fun ImportDialog(onMerge: () -> Unit, onReplace: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_choose_title)) },
        text = { Text(stringResource(R.string.import_choose_message)) },
        confirmButton = {
            TextButton(onClick = onMerge) { Text(stringResource(R.string.import_merge)) }
        },
        // The dialog lays out these buttons, then the confirm button, wrapping them if they
        // don't fit on one line.
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.confirm_dialog_cancel))
            }
            TextButton(onClick = onReplace, colors = removalButtonColors()) {
                Text(stringResource(R.string.import_replace_all))
            }
        }
    )
}

/**
 * Asks the scout which to keep, the phone's or the file's, for each of the merge's [choices]:
 * the name and unit number ([onChooseProfile]), and each badge and rank ([onChooseProgress]).
 * A full-screen dialog, as Material 3 suggests for a task with many choices, with a close
 * button and the button that merges ([onConfirm]) at its top. Under them, it names the ranks
 * that merging as chosen would un-earn. Closing it, or Back, calls [onDismiss], once the scout
 * confirms discarding their choices if they chose the file's for anything.
 */
@Composable
fun MergeDialog(
    choices: MergeChoices,
    onChooseProfile: (fromFile: Boolean) -> Unit,
    onChooseProgress: (id: String, fromFile: Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmingDiscard by rememberSaveable { mutableStateOf(false) }
    val close: () -> Unit = {
        if (choices.anyFromFile) confirmingDiscard = true else onDismiss()
    }
    Dialog(
        onDismissRequest = close,
        // Drawn behind the system bars, as the app's window is, rather than leaving them over
        // the dimmed page below. Its status bar takes its top row's color, with no band as pages
        // have: the top row already keeps the list, which scrolls under it, clear of the clock,
        // and shares its color with the status bar, as Material's top app bar does before a page
        // scrolls under it.
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        UseSystemBarIconsForTheme()
        Surface(modifier = Modifier.fillMaxSize()) {
            IgnoreTouchesAsItOpens {
                Column(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .heightIn(min = 64.dp)
                            .padding(horizontal = 4.dp)
                    ) {
                        IconButton(onClick = close) {
                            Icon(
                                painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.merge_close)
                            )
                        }
                        Text(
                            text = stringResource(R.string.merge_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                                .semantics { heading() }
                        )
                        TextButton(onClick = onConfirm) {
                            Text(stringResource(R.string.merge_confirm))
                        }
                    }
                    UnearnedRanks(choices.unearnedRanks)
                    MergeChoiceList(choices, onChooseProfile, onChooseProgress)
                }
            }
        }
        if (confirmingDiscard) {
            ConfirmDialog(
                title = stringResource(R.string.discard_changes_title),
                message = stringResource(R.string.discard_changes_message),
                confirmLabel = stringResource(R.string.discard_changes_confirm),
                onConfirm = {
                    confirmingDiscard = false
                    onDismiss()
                },
                onDismiss = { confirmingDiscard = false }
            )
        }
    }
}

/**
 * Gives a full-screen dialog's system bars icons that show on its page: dark on the light page,
 * light on the dark one, as enableEdgeToEdge() chooses for MainActivity's. Android takes the
 * icons from the top full-screen window, which is the dialog's, and that window doesn't get
 * MainActivity's: on Android 8 its navigation bar icons were white on the light page (#299). The
 * window draws no bar backgrounds, so the dialog's own page, its surface color, shows behind the
 * bars.
 */
@Composable
private fun UseSystemBarIconsForTheme() {
    val window = (LocalView.current.parent as DialogWindowProvider).window
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    SideEffect {
        WindowCompat.getInsetsController(window, window.decorView).run {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
}

/**
 * Ignores touches on [content] for the double-tap timeout after it opens, so the second tap of a
 * double tap on the import dialog's Merge doesn't choose an option under the finger, as screens
 * ignore it as they open (`rememberIgnoreTouchesNavEntryDecorator`).
 */
@Composable
private fun IgnoreTouchesAsItOpens(content: @Composable () -> Unit) {
    var opening by remember { mutableStateOf(true) }
    val timeoutMillis = LocalViewConfiguration.current.doubleTapTimeoutMillis
    LaunchedEffect(timeoutMillis) {
        delay(timeoutMillis)
        opening = false
    }
    Box {
        content()
        if (opening) {
            // A cover that takes touches and does nothing with them.
            Box(Modifier.matchParentSize().pointerInput(Unit) {})
        }
    }
}

/**
 * The ranks that merging as chosen would un-earn, such as "Star will no longer count as
 * earned.", as the removal dialogs name them (#259). Screen readers announce it as it appears,
 * but not again after the phone rotates ([AnnouncedText]). Nothing while there are none.
 */
@Composable
private fun UnearnedRanks(names: List<String>) {
    if (names.isEmpty()) return
    AnnouncedText(
        text = stringResource(
            R.string.merge_unearns,
            rememberBadgeNameListFormatter().format(names)
        ),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
    )
}

/** The merge's [choices], below what the dialog asks, scrolling under its header. */
@Composable
private fun MergeChoiceList(
    choices: MergeChoices,
    onChooseProfile: (fromFile: Boolean) -> Unit,
    onChooseProgress: (id: String, fromFile: Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.merge_message))
        choices.profile?.let { profile ->
            Choice(
                heading = stringResource(R.string.merge_profile_heading),
                phone = profileLines(profile.phone),
                file = profileLines(profile.file),
                fromFile = profile.fromFile,
                onChoose = onChooseProfile
            )
        }
        for (advancement in choices.advancements) {
            key(advancement.id) {
                Choice(
                    heading = advancement.name,
                    phone = summaryLines(advancement.phone, advancement.isRank),
                    file = summaryLines(advancement.file, advancement.isRank),
                    fromFile = advancement.fromFile,
                    onChoose = { onChooseProgress(advancement.id, it) }
                )
            }
        }
    }
}

/**
 * A choice between the phone's and the file's, under its [heading], with what each holds below
 * it ([phone], [file]). [onChoose] is told whether the scout chose the file's.
 */
@Composable
private fun Choice(
    heading: String,
    phone: List<String>,
    file: List<String>,
    fromFile: Boolean,
    onChoose: (fromFile: Boolean) -> Unit
) {
    Text(
        text = heading,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(top = 16.dp)
            .semantics { heading() }
    )
    Column(modifier = Modifier.selectableGroup()) {
        Option(
            label = stringResource(R.string.merge_this_phone),
            // Screen reader users moving from control to control don't hear the heading.
            labelDescription = stringResource(R.string.merge_this_phone_description, heading),
            details = phone,
            selected = !fromFile,
            onClick = { onChoose(false) }
        )
        Option(
            label = stringResource(R.string.merge_the_file),
            labelDescription = stringResource(R.string.merge_the_file_description, heading),
            details = file,
            selected = fromFile,
            onClick = { onChoose(true) }
        )
    }
}

/**
 * A radio button labeled [label], with [details] below it, that the whole row selects. Screen
 * readers read it as one: [labelDescription] in place of the label, then the details.
 */
@Composable
private fun Option(
    label: String,
    labelDescription: String,
    details: List<String>,
    selected: Boolean,
    onClick: () -> Unit
) {
    val readAs = readAsOneLabel(labelDescription, *details.toTypedArray())
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            // A label of its own rather than its lines merged, which TalkBack read without
            // "This phone" or "The file" once that line was scrolled off screen (#323).
            .clearAndSetSemantics { contentDescription = readAs }
            .padding(vertical = 8.dp)
    ) {
        // The row is the control, so the button isn't one of its own.
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            for (detail in details) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun profileLines(profile: Profile) = listOf(
    typedText(profile.name),
    stringResource(R.string.home_unit, typedText(profile.unitNumber))
)

/**
 * How far one side got with a badge or rank, as its list shows it, with the date it was
 * completed or earned on, and its requirements version when the two sides' differ.
 */
@Composable
private fun summaryLines(summary: ProgressSummary, isRank: Boolean): List<String> {
    val formatter = rememberCompletionDateFormatter()
    val fractionDone = summary.fractionDone
    val doneOn = summary.doneOn?.let { formatter.format(it) }
    val status = when {
        summary.done && isRank && doneOn != null -> stringResource(R.string.merge_earned_on, doneOn)
        summary.done && isRank -> stringResource(R.string.ranks_earned)
        summary.done && doneOn != null -> stringResource(R.string.merge_completed_on, doneOn)
        summary.done -> stringResource(R.string.badges_completed)
        fractionDone != null -> percentDoneDescription(fractionDone)
        else -> stringResource(R.string.badges_in_progress)
    }
    val version = summary.requirementsVersion?.let {
        stringResource(R.string.merge_requirements_version, formatter.format(it))
    }
    return listOfNotNull(status, version)
}
