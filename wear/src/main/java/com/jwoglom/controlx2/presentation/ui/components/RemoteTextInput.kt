package com.jwoglom.controlx2.presentation.ui.components

import android.app.Activity
import android.text.InputType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.app.RemoteInput
import androidx.wear.input.RemoteInputIntentHelper
import timber.log.Timber

/**
 * Returns a launcher that opens the Wear system text-input UI. When the user
 * confirms, [onResult] is invoked with the trimmed string; on cancel it is
 * invoked with `null`.
 *
 * Mirrors the pattern already used by
 * [com.jwoglom.controlx2.presentation.ui.PairingCodeEntryScreen] for 6-digit
 * code entry, generalized so Nightscout / xDrip settings can reuse it for
 * URL + API secret entry.
 */
@Composable
fun rememberRemoteTextInputLauncher(
    label: String,
    inputType: Int = InputType.TYPE_CLASS_TEXT,
    onResult: (String?) -> Unit,
): () -> Unit {
    val key = remember(label) { "remote_input_${label.hashCode()}" }
    val launcher: ActivityResultLauncher<android.content.Intent> =
        rememberLauncherForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                onResult(null)
                return@rememberLauncherForActivityResult
            }
            val value = RemoteInput.getResultsFromIntent(result.data)
                ?.getCharSequence(key)
                ?.toString()
                ?.trim()
            onResult(value)
        }

    return {
        val remoteInput = RemoteInput.Builder(key).setLabel(label).build()
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
        RemoteInputIntentHelper.putInputTypeExtra(intent, inputType)
        try {
            launcher.launch(intent)
        } catch (e: Exception) {
            Timber.e(e, "rememberRemoteTextInputLauncher: failed to launch RemoteInputIntent")
            onResult(null)
        }
    }
}
