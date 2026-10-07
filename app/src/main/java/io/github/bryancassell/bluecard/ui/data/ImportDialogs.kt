package io.github.bryancassell.bluecard.ui.data

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.ui.badge.rememberCompletionDateFormatter
import io.github.bryancassell.bluecard.ui.badges.percentDoneDescription
import io.github.bryancassell.bluecard.ui.removalButtonColors

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
 * button ([onDismiss], as is Back) and the button that merges ([onConfirm]) at its top.
 */
@Composable
fun MergeDialog(
    choices: MergeChoices,
    onChooseProfile: (fromFile: Boolean) -> Unit,
    onChooseProgress: (id: String, fromFile: Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        // Drawn behind the system bars, as the app's pages are, rather than leaving them over
        // the dimmed page below.
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 4.dp)
                ) {
                    IconButton(onClick = onDismiss) {
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
        Option(stringResource(R.string.merge_this_phone), phone, !fromFile) { onChoose(false) }
        Option(stringResource(R.string.merge_the_file), file, fromFile) { onChoose(true) }
    }
}

/** A radio button labeled [label], with [details] below it, that the whole row selects. */
@Composable
private fun Option(label: String, details: List<String>, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
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
private fun profileLines(profile: Profile) =
    listOf(profile.name, stringResource(R.string.merge_unit, profile.unitNumber))

/**
 * How far one side got with a badge or rank, as its list shows it, and its requirements version
 * when the two sides' differ.
 */
@Composable
private fun summaryLines(summary: ProgressSummary, isRank: Boolean): List<String> {
    val formatter = rememberCompletionDateFormatter()
    val fractionDone = summary.fractionDone
    val status = when {
        summary.done && isRank -> stringResource(R.string.ranks_earned)
        summary.done -> stringResource(R.string.badges_completed)
        fractionDone != null -> percentDoneDescription(fractionDone)
        else -> stringResource(R.string.badges_in_progress)
    }
    val version = summary.requirementsVersion?.let {
        stringResource(R.string.merge_requirements_version, formatter.format(it))
    }
    return listOfNotNull(status, version)
}
