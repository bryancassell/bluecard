package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntryDecorator

/**
 * Returns a decorator that paints each page with the theme's background, the color the app's
 * Scaffold paints behind the pages, as an activity's window background does.
 *
 * Page transitions slide one page over another, and the page underneath doesn't fade out, so
 * without this a page would show the one under it through its gaps.
 */
@Composable
fun <T : Any> rememberPageBackgroundNavEntryDecorator(): NavEntryDecorator<T> = remember {
    NavEntryDecorator(
        decorate = { entry ->
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                entry.Content()
            }
        }
    )
}
