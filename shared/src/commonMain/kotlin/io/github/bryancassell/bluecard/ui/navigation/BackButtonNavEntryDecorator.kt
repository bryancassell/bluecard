package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.back
import io.github.bryancassell.bluecard.resources.ic_arrow_back_ios_new
import io.github.bryancassell.bluecard.ui.statusBarBand
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Whether a page has a Back button on iOS, in its entry's metadata. Pages have one unless it says no. */
data object HasBackButton : NavMetadataKey<Boolean>

/**
 * Metadata for a page that has nothing to go back to, Home or Onboarding. It's the page's own,
 * rather than whether it's first in the back stack, so a page leaving as the back stack is
 * replaced, as Onboarding is once the profile is saved, doesn't gain a button as it fades out.
 */
val WithoutBackButton: Map<String, Any> = metadata { put(HasBackButton, false) }

/**
 * Returns a decorator that puts a bar with a Back button above each page but those
 * [WithoutBackButton], for iOS. iOS has no system Back, and nothing on screen shows its edge
 * swipe, so without the button a scout who didn't know the swipe couldn't leave a page (#351).
 * [onBack] goes back from a page, by its entry's content key, as the button is pressed.
 *
 * The button shows only a chevron, which screen readers read as "Back". Apple's guidelines say
 * of the Back button: "Prefer the standard symbols for each, and don't use a text label that
 * says Back or Close" (https://developer.apple.com/design/human-interface-guidelines/toolbars).
 * The bar is the color of the band behind the status bar ([statusBarBand]), so the two read as
 * one, and a page scrolled up to it ends at its edge rather than running into the chevron
 * (#315). It slides with its page.
 */
@Composable
fun rememberBackButtonNavEntryDecorator(
    onBack: (contentKey: Any) -> Unit
): NavEntryDecorator<NavKey> {
    val currentOnBack by rememberUpdatedState(onBack)
    return remember {
        NavEntryDecorator(decorate = { entry ->
            Column(Modifier.fillMaxSize()) {
                if (entry.metadata[HasBackButton] != false) {
                    BackButtonBar(onBack = { currentOnBack(entry.contentKey) })
                }
                Box(Modifier.weight(1f)) { entry.Content() }
            }
        })
    }
}

@Composable
private fun BackButtonBar(onBack: () -> Unit) {
    // The icon button's 48dp touch target sets the bar's height, near iOS's 44pt bars.
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.statusBarBand)) {
        IconButton(onClick = onBack) {
            Icon(
                painterResource(Res.drawable.ic_arrow_back_ios_new),
                contentDescription = stringResource(Res.string.back),
                // iOS tints its Back button with the app's accent color.
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
