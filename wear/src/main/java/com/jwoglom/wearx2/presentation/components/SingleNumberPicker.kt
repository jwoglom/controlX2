package com.jwoglom.wearx2.presentation.components

import android.view.MotionEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Picker
import androidx.wear.compose.material.PickerDefaults
import androidx.wear.compose.material.PickerScope
import androidx.wear.compose.material.PickerState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberPickerState
import com.google.android.horologist.composables.R
import timber.log.Timber

@Composable
fun SingleNumberPicker(
    label: String? = null,
    onNumberConfirm: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maxNumber: Int = 30,
    minNumber: Int = 0,
    defaultNumber: Int = minNumber,
) {
    // Omit scaling according to Settings > Display > Font size for this screen,
    val typography = MaterialTheme.typography.copy(
        display1 = MaterialTheme.typography.display1.copy(
            fontSize = with(LocalDensity.current) { 40.dp.toSp() }
        )
    )
    val leftState = rememberPickerState(
        initialNumberOfOptions = maxNumber + 10, // Add extra blank options to prevent accidental selection of the maximum
        initiallySelectedOption = defaultNumber - minNumber
    )

    MaterialTheme(typography = typography) {
        var selectedColumn by remember { mutableStateOf(0) }
        val textStyle = MaterialTheme.typography.display1
        val focusRequester1 = remember { FocusRequester() }

        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (label != null) {
                Spacer(Modifier.height(8.dp))
                CompactChip(
                    onClick = { },
                    modifier = Modifier.size(width = 50.dp, height = 40.dp).zIndex(99F),
                    label = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = label,
                                color = MaterialTheme.colors.onPrimary,
                                style = MaterialTheme.typography.button
                            )
                        }
                    },
                    colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.secondary),
                    contentPadding = PaddingValues(vertical = 0.dp),
                )
            }

            Spacer(
                Modifier
                    .fillMaxWidth()
                    .weight(0.5f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Spacer(Modifier.width(8.dp))
                PickerWithRSB(
                    readOnly = false,
                    state = leftState,
                    focusRequester = focusRequester1,
                    modifier = Modifier.size(64.dp, 100.dp),
                    readOnlyLabel = { LabelText("") }
                ) { leftNumber: Int ->
                    if (leftNumber > maxNumber) {
                        Spacer(Modifier.height(50.dp))
                    } else {
                        NumberPiece(
                            selected = true,
                            onSelected = {  },
                            text = "${minNumber+leftNumber}",
                            style = textStyle
                        )
                    }

                }
                Spacer(Modifier.width(8.dp))
            }
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .weight(0.5f)
            )
            Button(onClick = {
                var leftNumber = minNumber + leftState.selectedOption

                // selecting a blank option -- return 0
                if (leftNumber > maxNumber) {
                    leftNumber = 0
                }
                Timber.i("NumberPicker: $leftNumber")
                onNumberConfirm(leftNumber)
            }) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Confirm",
                    modifier = Modifier
                        .size(24.dp)
                        .wrapContentSize(align = Alignment.Center)
                )
            }
            Spacer(Modifier.height(8.dp))
            LaunchedEffect(selectedColumn) {
                focusRequester1.requestFocus()
            }
        }
    }
}
