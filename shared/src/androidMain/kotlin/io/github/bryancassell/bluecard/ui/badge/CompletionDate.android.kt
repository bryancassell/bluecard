package io.github.bryancassell.bluecard.ui.badge

import android.text.TextUtils
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.LayoutDirection

/** The device's first locale's direction, as Material 3 reads it from the configuration. */
@Composable
@ReadOnlyComposable
internal actual fun pickerLayoutDirection(): LayoutDirection =
    when (TextUtils.getLayoutDirectionFromLocale(LocalConfiguration.current.locales[0])) {
        View.LAYOUT_DIRECTION_RTL -> LayoutDirection.Rtl
        else -> LayoutDirection.Ltr
    }
