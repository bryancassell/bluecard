package io.github.bryancassell.bluecard

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.bryancassell.bluecard.ui.theme.BlueCardTheme

// Previews live in *Preview.kt files, which test coverage ignores: they only run
// in Android Studio.

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BlueCardTheme {
        Greeting("Scout")
    }
}
