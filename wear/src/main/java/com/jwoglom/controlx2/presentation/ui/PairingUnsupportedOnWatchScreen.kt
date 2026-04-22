package com.jwoglom.controlx2.presentation.ui

import androidx.compose.runtime.Composable

/**
 * Shown when `FROM_PUMP_MISSING_PAIRING_CODE` arrives but the pump uses a 16-character
 * pairing code — impractical to enter on a watch. Instructs the user to pair via
 * the phone and then switch the watch to pump-host in settings.
 */
@Composable
fun PairingUnsupportedOnWatchScreen() {
    FullScreenText(
        text = "This pump uses a 16-character pairing code. Pair from the phone first, then switch the watch to pump-host in settings.",
    )
}
