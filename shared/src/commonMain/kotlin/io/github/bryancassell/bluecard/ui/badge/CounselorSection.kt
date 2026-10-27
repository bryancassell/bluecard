package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.bryancassell.bluecard.data.progress.Counselor
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.badge_detail_add_counselor
import io.github.bryancassell.bluecard.resources.badge_detail_call_counselor
import io.github.bryancassell.bluecard.resources.badge_detail_counselor
import io.github.bryancassell.bluecard.resources.badge_detail_edit_counselor
import io.github.bryancassell.bluecard.resources.badge_detail_email_counselor
import io.github.bryancassell.bluecard.resources.badge_detail_no_email_app
import io.github.bryancassell.bluecard.resources.badge_detail_no_phone_app
import io.github.bryancassell.bluecard.resources.ic_call
import io.github.bryancassell.bluecard.resources.ic_email
import io.github.bryancassell.bluecard.resources.ic_person
import io.github.bryancassell.bluecard.ui.OtherAppStarter
import io.github.bryancassell.bluecard.ui.rememberOtherAppStarter
import io.github.bryancassell.bluecard.ui.typedText
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The badge's merit badge [counselor], with a button to enter one or edit it. Tapping the phone
 * number opens the phone app to call it, and tapping the email address opens an email app, each
 * with [startOtherApp] ([rememberOtherAppStarter]). The phone number shows as [phone], formatted,
 * or as typed if that's null, and is dialed as typed.
 */
@Composable
fun CounselorSection(
    counselor: Counselor?,
    phone: String?,
    onEdit: () -> Unit,
    startOtherApp: OtherAppStarter,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.badge_detail_counselor),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { heading() }
        )
        counselor?.name?.let { CounselorItem(Res.drawable.ic_person, it) }
        counselor?.phone?.let {
            val noApp = stringResource(Res.string.badge_detail_no_phone_app)
            ContactItem(
                icon = Res.drawable.ic_call,
                text = phone ?: it,
                onClickLabel = stringResource(Res.string.badge_detail_call_counselor),
                onClick = { startOtherApp.start(noApp) { dial(it) } }
            )
        }
        counselor?.email?.let {
            val noApp = stringResource(Res.string.badge_detail_no_email_app)
            ContactItem(
                icon = Res.drawable.ic_email,
                text = it,
                onClickLabel = stringResource(Res.string.badge_detail_email_counselor),
                onClick = { startOtherApp.start(noApp) { email(it) } }
            )
        }
        // Lines the button's text up with the heading's.
        TextButton(onClick = onEdit, modifier = Modifier.padding(horizontal = 4.dp)) {
            val label = if (counselor == null) {
                Res.string.badge_detail_add_counselor
            } else {
                Res.string.badge_detail_edit_counselor
            }
            Text(stringResource(label))
        }
    }
}

/** A detail of the counselor that the scout typed, with an icon that says which. */
@Composable
private fun CounselorItem(icon: DrawableResource, text: String, modifier: Modifier = Modifier) {
    ListItem(
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        headlineContent = { Text(typedText(text)) },
        modifier = modifier
    )
}

/** A way to reach the counselor, which opens another app with [onClick] when tapped. */
@Composable
private fun ContactItem(
    icon: DrawableResource,
    text: String,
    onClickLabel: String,
    onClick: () -> Unit
) {
    CounselorItem(
        icon = icon,
        text = text,
        // ListItem reads as one item to screen readers, announced as a button that does
        // onClickLabel.
        modifier = Modifier.clickable(
            onClickLabel = onClickLabel,
            role = Role.Button,
            onClick = onClick
        )
    )
}
