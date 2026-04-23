package com.jwoglom.controlx2.presentation.ui

import android.app.Activity
import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.input.RemoteInputIntentHelper
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.MainActivity
import timber.log.Timber

private const val REMOTE_INPUT_KEY = "pairing_code"
const val PAIRING_CODE_LENGTH = 6

/**
 * Launches the Wear system numeric-input UI via [RemoteInputIntentHelper] so the
 * user can enter a 6-digit pairing code. Validates the returned string is exactly
 * 6 digits; on success, delegates to [MainActivity.submitPairingCode] which
 * persists the code via [com.jwoglom.controlx2.pump.pairing.PairingCodeEntry] and
 * dispatches the next step.
 *
 * The screen auto-launches the input UI on first composition unless a prior
 * pairing error is showing — in which case the user taps to re-open it.
 */
@Composable
fun PairingCodeEntryScreen() {
    val context = LocalContext.current
    val dataStore = LocalDataStore.current
    val errorState = dataStore.pumpPairingError.observeAsState()
    val localError = remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            Timber.i("PairingCodeEntryScreen: user cancelled input")
            return@rememberLauncherForActivityResult
        }
        val code = result.data?.let { RemoteInput.getResultsFromIntent(it) }
            ?.getCharSequence(REMOTE_INPUT_KEY)
            ?.toString()
            ?.trim()
        when {
            code == null -> {
                localError.value = "No code entered"
            }
            code.length != PAIRING_CODE_LENGTH || !code.all(Char::isDigit) -> {
                localError.value = "Must be $PAIRING_CODE_LENGTH digits"
            }
            else -> {
                localError.value = null
                val activity = context as? MainActivity
                if (activity != null) {
                    activity.submitPairingCode(code)
                } else {
                    Timber.e("PairingCodeEntryScreen: context is not MainActivity")
                }
            }
        }
    }

    val launchInput: () -> Unit = {
        val remoteInput = RemoteInput.Builder(REMOTE_INPUT_KEY)
            .setLabel("Pairing code")
            .build()
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
        try {
            launcher.launch(intent)
        } catch (e: Exception) {
            Timber.e(e, "PairingCodeEntryScreen: failed to launch RemoteInputIntent")
            localError.value = "Input unavailable"
        }
    }

    // Auto-launch on first composition if no prior error is visible.
    LaunchedEffect(Unit) {
        if (errorState.value == null && localError.value == null) {
            launchInput()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val banner = localError.value ?: errorState.value
        if (banner != null) {
            Text(
                text = banner,
                color = Color.Red,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Text(
            text = "Enter pump pairing code",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Chip(
            onClick = launchInput,
            label = { Text("Enter code", fontSize = 12.sp) },
            colors = ChipDefaults.primaryChipColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
