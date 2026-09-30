package io.github.bryancassell.bluecard.ui.badge

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.data.progress.Counselor
import io.github.bryancassell.bluecard.ui.typedText

/**
 * The badge's merit badge [counselor], with a button to enter one or edit it. Tapping the phone
 * number opens the phone app to call it, and tapping the email address opens an email app.
 */
@Composable
fun CounselorSection(counselor: Counselor?, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.badge_detail_counselor),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { heading() }
        )
        counselor?.name?.let { CounselorItem(R.drawable.ic_person, it) }
        counselor?.phone?.let {
            // ACTION_DIAL fills in the number without calling it, so it needs no permission.
            ContactItem(
                icon = R.drawable.ic_call,
                text = it,
                intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", it, null)),
                onClickLabel = stringResource(R.string.badge_detail_call_counselor),
                noApp = stringResource(R.string.badge_detail_no_phone_app)
            )
        }
        counselor?.email?.let {
            // Only email apps handle ACTION_SENDTO with a mailto: address. It keeps @ and + as
            // they are, since some apps show the address as the link spells it, and encodes
            // anything that would change the link, such as ? or #.
            ContactItem(
                icon = R.drawable.ic_email,
                text = it,
                intent = Intent(Intent.ACTION_SENDTO, "mailto:${Uri.encode(it, "@+")}".toUri()),
                onClickLabel = stringResource(R.string.badge_detail_email_counselor),
                noApp = stringResource(R.string.badge_detail_no_email_app)
            )
        }
        // Lines the button's text up with the heading's.
        TextButton(onClick = onEdit, modifier = Modifier.padding(horizontal = 4.dp)) {
            val label = if (counselor == null) {
                R.string.badge_detail_add_counselor
            } else {
                R.string.badge_detail_edit_counselor
            }
            Text(stringResource(label))
        }
    }
}

/** A detail of the counselor that the scout typed, with an icon that says which. */
@Composable
private fun CounselorItem(@DrawableRes icon: Int, text: String, modifier: Modifier = Modifier) {
    ListItem(
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        headlineContent = { Text(typedText(text)) },
        modifier = modifier
    )
}

/**
 * A way to reach the counselor, which starts [intent] when tapped, or says [noApp] if no app
 * can handle it.
 */
@Composable
private fun ContactItem(
    @DrawableRes icon: Int,
    text: String,
    intent: Intent,
    onClickLabel: String,
    noApp: String
) {
    val context = LocalContext.current
    CounselorItem(
        icon = icon,
        text = text,
        // ListItem reads as one item to screen readers, announced as a button that does
        // onClickLabel.
        modifier = Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button) {
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, noApp, Toast.LENGTH_SHORT).show()
            }
        }
    )
}
