package com.jwoglom.wearx2.presentation.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.CurvedAlignment
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.foundation.curvedComposable
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.TimeTextDefaults
import androidx.wear.compose.material.curvedText
import com.jwoglom.wearx2.LocalDataStore


@Composable
fun TopText(
    visible: Boolean,
    modifier: Modifier = Modifier,
    startText: String? = null
) {
    val textStyle = TimeTextDefaults.timeTextStyle()
//    val debugWarning = remember {
//        val isEmulator = Build.PRODUCT.startsWith("sdk_gwear")
//
//        if (BuildConfig.DEBUG && !isEmulator) {
//            "Debug"
//        } else {
//            null
//        }
//    }
//    val showWarning = debugWarning != null
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val visibleText = startText != null
        val ds = LocalDataStore.current
        val cgmSessionState = ds.cgmSessionState.observeAsState()
        val cgmReading = ds.cgmReading.observeAsState()
        val cgmDelta = ds.cgmDelta.observeAsState()
        val cgmStatusText = ds.cgmStatusText.observeAsState()
        val cgmHighLowState = ds.cgmHighLowState.observeAsState()
        val cgmDeltaArrow = ds.cgmDeltaArrow.observeAsState()
        TimeText(
            modifier = modifier,
            startCurvedContent = if (visibleText) {
                {
                    curvedText(
                        text = startText!!,
                        style = CurvedTextStyle(textStyle)
                    )
                }
            } else null,
            startLinearContent = if (visibleText) {
                {
                    Text(
                        text = startText!!,
                        style = textStyle
                    )
                }
            } else null,
            // Trailing text is against Wear UX guidance
            endCurvedContent = {
                currentCGMTextCurvedExcludingDeltaArrow(
                    CurvedTextStyle(textStyle),
                    cgmSessionState.value,
                    cgmStatusText.value,
                    cgmReading.value,
                    cgmDeltaArrow.value,
                    cgmHighLowState.value
                )
                curvedComposable (radialAlignment = CurvedAlignment.Radial.Outer) {
                    CurrentCGMTextDeltaArrow(textStyle, modifier = Modifier.padding(start = 2.dp))
                }
            },
            endLinearContent = {
                CurrentCGMText()
            }
        )
    }
}

@Preview(
    apiLevel = 26,
    uiMode = Configuration.UI_MODE_TYPE_WATCH,
    showSystemUi = true,
    device = Devices.WEAR_OS_LARGE_ROUND
)
@Preview(
    apiLevel = 26,
    uiMode = Configuration.UI_MODE_TYPE_WATCH,
    showSystemUi = true,
    device = Devices.WEAR_OS_SQUARE
)
@Preview(
    apiLevel = 26,
    uiMode = Configuration.UI_MODE_TYPE_WATCH,
    showSystemUi = true,
    device = Devices.WEAR_OS_SMALL_ROUND
)
// This will only be rendered properly in AS Chipmunk and beyond
@Composable
fun PreviewTopText() {
    CustomTimeText(
        visible = true,
        startText = "Start text"
    )
}
