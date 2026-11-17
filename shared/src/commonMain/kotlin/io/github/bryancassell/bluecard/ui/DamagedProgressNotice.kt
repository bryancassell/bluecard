package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.Composable

/**
 * Tells the scout that the progress saved on the phone was damaged, so BlueCard started over
 * without it. Only OK closes it ([onDismiss]); Back and taps outside don't, so it isn't missed.
 * A scout's records may cover years of work, so they're never lost without the scout knowing
 * (#70). It doesn't suggest restoring Android's backup: that means reinstalling BlueCard, which
 * deletes what's on the phone, and works only if the nightly backup ran before the damage and
 * hasn't run since. It's Material's dialog on Android, and UIKit's alert on iOS.
 */
@Composable
expect fun DamagedProgressNotice(onDismiss: () -> Unit)
