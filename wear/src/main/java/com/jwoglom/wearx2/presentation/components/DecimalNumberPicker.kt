package com.jwoglom.wearx2.presentation.components

import android.view.MotionEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Picker
import androidx.wear.compose.material.PickerDefaults
import androidx.wear.compose.material.PickerScope
import androidx.wear.compose.material.PickerState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberPickerState
import com.google.android.horologist.compose.rotaryinput.onRotaryInputAccumulated
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DecimalNumberPicker(
    onNumberConfirm: (Double) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    maxNumber: Int = 30,
    defaultNumber: Double? = null,
    rotaryScrollWeight: Float = 1f,
    labelColors: Colors = MaterialTheme.colors,
) {
    // Omit scaling according to Settings > Display > Font size for this screen,
    val typography = MaterialTheme.typography.copy(
        display1 = MaterialTheme.typography.display1.copy(
            fontSize = with(LocalDensity.current) { 40.dp.toSp() }
        )
    )

    val leftState = rememberPickerState(
        initialNumberOfOptions = maxNumber + 10, // Add extra blank options to prevent accidental selection of the maximum
        initiallySelectedOption = when (defaultNumber) {
            null -> 0
            else -> defaultNumber.toInt()
        }
    )
    val rightState = rememberPickerState(
        initialNumberOfOptions = 10 * (maxNumber + 10),
        initiallySelectedOption = when (defaultNumber) {
            null -> 0
            else -> ((defaultNumber * 10) % 10).toInt()
        }
    )

    val coroutineScope = rememberCoroutineScope()

    fun buildNumber(): Double {
        var leftNumber = leftState.selectedOption
        var rightNumber = rightState.selectedOption % 10

        // selecting a blank option -- return 0
        if (leftNumber > maxNumber) {
            leftNumber = 0
            rightNumber = 0
        }
        return leftNumber + (1.0*rightNumber)/10
    }

    MaterialTheme(typography = typography) {
        var selectedColumn by remember { mutableStateOf(1) }
        val textStyle = MaterialTheme.typography.display1
        val focusRequesterLeft = remember { FocusRequester() }
        val focusRequesterRight = remember { FocusRequester() }

        var rotaryScrollFix: Job? = null
        fun runRotaryScrollFix() {
            rotaryScrollFix?.cancel()
            rotaryScrollFix = coroutineScope.launch {
                Timber.d("coroutine: delay")
                delay(250)

                if (!leftState.isScrollInProgress && !rightState.isScrollInProgress) {
                    Timber.d("coroutine: scroll")
                    leftState.scrollToOption(leftState.selectedOption)
                    rightState.scrollToOption(rightState.selectedOption)
                } else {
                    Timber.d("coroutine: skipped")
                }
            }
            rotaryScrollFix?.start()
        }

        LaunchedEffect (leftState.selectedOption) {
            if (selectedColumn == 0) {
                if (rightState.selectedOption != leftState.selectedOption * 10 + rightState.selectedOption % 10 && rightState.selectedOption <= maxNumber) {
                    rightState.scrollToOption(leftState.selectedOption * 10 + rightState.selectedOption % 10)
                }
            }
            runRotaryScrollFix()
        }

        LaunchedEffect (rightState.selectedOption) {
            if (selectedColumn == 1) {
                if (leftState.selectedOption != rightState.selectedOption / 10) {
                    leftState.scrollToOption(rightState.selectedOption / 10)
                }
            }
            runRotaryScrollFix()
        }

        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            if (label != null) {
                Spacer(Modifier.height(8.dp))
                CompactChip(
                    onClick = { },
                    modifier = Modifier
                        .size(width = 50.dp, height = 40.dp)
                        .zIndex(99F),
                    label = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = label,
                                color = labelColors.primary,
                                style = MaterialTheme.typography.button
                            )
                        }
                    },
                    colors = ChipDefaults.chipColors(backgroundColor = labelColors.onPrimary),
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
                    readOnly = selectedColumn != 0,
                    state = leftState,
                    focusRequester = focusRequesterLeft,
                    modifier = Modifier
                        .size(64.dp, 100.dp)
                        .onRotaryScrollEvent {
                            coroutineScope.launch {
                                leftState.scrollBy(it.verticalScrollPixels * rotaryScrollWeight)
                            }
                            true
                        },
                    readOnlyLabel = { LabelText("") }
                ) { leftNumber: Int ->
                    if (leftNumber > maxNumber) {
                        Spacer(Modifier.height(50.dp))
                    } else {
                        NumberPiece(
                            selected = selectedColumn == 0,
                            onSelected = { selectedColumn = 0 },
                            text = "%2d".format(leftNumber),
                            style = textStyle
                        )
                    }

                }

                Spacer(Modifier.width(2.dp))
                Text(
                    text = ".",
                    style = textStyle,
                    color = MaterialTheme.colors.onBackground
                )
                Spacer(Modifier.width(2.dp))

                PickerWithRSB(
                    readOnly = selectedColumn != 1,
                    state = rightState,
                    focusRequester = focusRequesterRight,
                    modifier = Modifier
                        .size(64.dp, 100.dp)
                        .onRotaryScrollEvent {
                            coroutineScope.launch {
                                rightState.scrollBy(it.verticalScrollPixels * rotaryScrollWeight)
                            }
                            true
                        },
                    readOnlyLabel = { LabelText("") },
                ) { rightNumber: Int ->
                    NumberPiece(
                        selected = selectedColumn == 1,
                        onSelected = { selectedColumn = 1 },
                        text = "%1d".format(rightNumber % 10),
                        style = textStyle
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .weight(0.5f)
            )
            Button(onClick = {
                val confirmedNumber = buildNumber()
                Timber.i("DecimalNumberPicker: $confirmedNumber (${leftState.selectedOption}, ${rightState.selectedOption})")
                onNumberConfirm(confirmedNumber)
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
                listOf(focusRequesterLeft, focusRequesterRight)[selectedColumn].requestFocus()
            }
        }
    }
}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun NumberPiece(
    selected: Boolean,
    onSelected: () -> Unit,
    text: String,
    style: TextStyle
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val modifier = Modifier
            .align(Alignment.Center)
            .wrapContentSize()
        Text(
            text = text,
            maxLines = 1,
            style = style,
            color =
            if (selected) MaterialTheme.colors.secondary
            else MaterialTheme.colors.onBackground,
            modifier =
            if (selected) modifier
            else modifier.pointerInteropFilter {
                if (it.action == MotionEvent.ACTION_DOWN) onSelected()
                true
            }
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PickerWithRSB(
    state: PickerState,
    readOnly: Boolean,
    modifier: Modifier,
    focusRequester: FocusRequester,
    readOnlyLabel: @Composable (BoxScope.() -> Unit)? = null,
    flingBehavior: FlingBehavior = PickerDefaults.flingBehavior(state = state),
    option: @Composable PickerScope.(optionIndex: Int) -> Unit,
) {
    Picker(
        state = state,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable(),
        flingBehavior = flingBehavior,
        readOnly = readOnly,
        readOnlyLabel = readOnlyLabel,
        option = option
    )
}

@Composable
internal fun BoxScope.LabelText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption1,
        color = MaterialTheme.colors.onSurfaceVariant,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = 8.dp)
    )
}