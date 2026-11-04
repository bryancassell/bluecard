package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.ic_check
import org.jetbrains.compose.resources.painterResource

// Checkboxes and radio buttons take iOS's form on iOS (PRD.md, iOS look and conventions): a switch
// at the row's end, and a checkmark at the chosen option's end. Apple describes checkboxes and radio
// buttons only for macOS, and for iOS says "Use the switch toggle style only in a list row"
// (https://developer.apple.com/design/human-interface-guidelines/toggles), and that a list of
// options marks the chosen one with "an image — such as a checkmark"
// (https://developer.apple.com/design/human-interface-guidelines/lists-and-tables). The rows are in
// common code, with a parameter for iOS's form, so Android's Compose UI tests can check it: nothing
// runs Compose UI tests on iOS yet.

internal const val TOGGLE_SWITCH_TAG = "toggleSwitch"
internal const val CHOICE_CHECKMARK_TAG = "choiceCheckmark"

/** Whether checkboxes and radio buttons take iOS's form. */
internal expect val usesIosToggles: Boolean

/**
 * The platform's own switch, showing [checked]: UIKit's on iOS, and Material's on Android, where
 * only tests show one. It takes no touches, since the row it's in is the control ([ToggleRow]).
 * [background] is the color behind it, which UIKit's is drawn on.
 */
@Composable
internal expect fun PlatformSwitch(
    checked: Boolean,
    background: Color,
    modifier: Modifier = Modifier
)

/**
 * A row labeled [label] that toggles [checked] wherever it's tapped, which screen readers read as
 * one control. It shows a checkbox at its start, or with [iosSwitch], the platform's switch at its
 * end, as iOS's own rows do. A long label wraps. [background] is the color the row is on, such as
 * the page's, which UIKit's switch is drawn on: nothing on Android shows a wrong one.
 */
@Composable
internal fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    background: Color,
    modifier: Modifier = Modifier,
    iosSwitch: Boolean = usesIosToggles
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                interactionSource = null,
                // No grey while held, as on iOS's own switch rows. The grey would also cover
                // UIKit's switch, which is under Compose's canvas, as PlatformSwitch says.
                indication = if (iosSwitch) FocusAndHoverHighlight else LocalIndication.current,
                // Compose gives iOS's VoiceOver a switch's trait for Role.Switch, and none for
                // Role.Checkbox (SemanticConfigurationUtils.ios.kt, Compose UI 1.12.2).
                role = if (iosSwitch) Role.Switch else Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp)
    ) {
        // The row is the control, so neither the checkbox nor the switch is one of its own.
        if (!iosSwitch) Checkbox(checked = checked, onCheckedChange = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            // Leaves the switch its room beside a label that wraps.
            modifier = Modifier.weight(1f).padding(vertical = 8.dp)
        )
        if (iosSwitch) PlatformSwitch(checked, background, Modifier.testTag(TOGGLE_SWITCH_TAG))
    }
}

/**
 * One of a few options, which tapping anywhere on the row chooses, with [content] beside its
 * mark. It shows a radio button at its start, or with [iosCheckmark], a checkmark at its end while
 * [selected]. Screen readers read it as one option, labeled [contentDescription].
 */
@Composable
internal fun ChoiceRow(
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    iosCheckmark: Boolean = usesIosToggles,
    content: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            // A label of its own rather than its lines merged, which TalkBack read without the
            // first one once it was scrolled off screen (#323).
            .clearAndSetSemantics { this.contentDescription = contentDescription }
            .padding(vertical = 8.dp)
    ) {
        // The row is the control, so the radio button isn't one of its own.
        if (!iosCheckmark) RadioButton(selected = selected, onClick = null)
        Box(Modifier.weight(1f)) { content() }
        if (iosCheckmark) {
            // Its room is kept while it's not chosen, so choosing it doesn't rewrap its text and
            // move the option below.
            Box(Modifier.size(24.dp)) {
                if (selected) {
                    Icon(
                        painterResource(Res.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag(CHOICE_CHECKMARK_TAG)
                    )
                }
            }
        }
    }
}
