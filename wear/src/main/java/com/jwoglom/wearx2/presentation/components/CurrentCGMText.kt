package com.jwoglom.wearx2.presentation.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.CurvedModifier
import androidx.wear.compose.foundation.CurvedScope
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.foundation.background
import androidx.wear.compose.material.LocalTextStyle
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.curvedText
import com.google.common.base.Strings
import com.jwoglom.wearx2.LocalDataStore
import com.jwoglom.wearx2.presentation.defaultTheme
import com.jwoglom.wearx2.presentation.redTheme

@Composable
fun CurrentCGMText(
    textStyle: TextStyle? = null,
) {
    val ds = LocalDataStore.current
    val cgmSessionState = ds.cgmSessionState.observeAsState()
    val cgmReading = ds.cgmReading.observeAsState()
    val cgmDelta = ds.cgmDelta.observeAsState()
    val cgmStatusText = ds.cgmStatusText.observeAsState()
    val cgmHighLowState = ds.cgmHighLowState.observeAsState()
    val cgmDeltaArrow = ds.cgmDeltaArrow.observeAsState()
    val displayText = when (cgmSessionState.value) {
        "Starting", "Stopped", "Stopping", "Unknown" -> cgmStatusText.value!!
        else -> when {
            !Strings.isNullOrEmpty(cgmStatusText.value) -> cgmStatusText.value!!
            else -> when {
                cgmReading.value != null && cgmDeltaArrow.value != null -> "${cgmReading.value} ${cgmDeltaArrow.value}"
                cgmReading.value != null -> "${cgmReading.value}"
                else -> ""
            }
        }
    }
    val primaryColor = when (cgmHighLowState.value) {
        "HIGH" -> redTheme.colors.secondary
        "LOW" -> redTheme.colors.primary
        else -> MaterialTheme.colors.primary
    }

    Text(
        modifier = Modifier
            .background(Color.Transparent),
        textAlign = TextAlign.Center,
        color = primaryColor,
        text = displayText,
        style = textStyle ?: LocalTextStyle.current,
    )
}

@Composable
fun CurrentCGMTextDeltaArrow(
    textStyle: TextStyle? = null,
    modifier: Modifier = Modifier,
) {
    val ds = LocalDataStore.current
    val cgmSessionState = ds.cgmSessionState.observeAsState()
    val cgmReading = ds.cgmReading.observeAsState()
    val cgmDelta = ds.cgmDelta.observeAsState()
    val cgmStatusText = ds.cgmStatusText.observeAsState()
    val cgmHighLowState = ds.cgmHighLowState.observeAsState()
    val cgmDeltaArrow = ds.cgmDeltaArrow.observeAsState()
    val displayText = when (cgmSessionState.value) {
        "Starting", "Stopped", "Stopping", "Unknown" -> ""
        else -> when {
            !Strings.isNullOrEmpty(cgmStatusText.value) -> ""
            else -> when {
                cgmDeltaArrow.value != null -> "${cgmDeltaArrow.value}"
                else -> ""
            }
        }
    }
    val primaryColor = when (cgmHighLowState.value) {
        "HIGH" -> redTheme.colors.secondary
        "LOW" -> redTheme.colors.primary
        else -> defaultTheme.colors.primary
    }

    Text(
        modifier = modifier
            .background(Color.Transparent),
        textAlign = TextAlign.Center,
        color = primaryColor,
        text = displayText,
        style = textStyle ?: LocalTextStyle.current,
    )
}


fun CurvedScope.currentCGMTextCurvedExcludingDeltaArrow(
    textStyle: CurvedTextStyle? = null,
    cgmSessionState: String?,
    cgmStatusText: String?,
    cgmReading: Int?,
    cgmDeltaArrow: String?,
    cgmHighLowState: String?,
) {
    val displayText = when (cgmSessionState) {
        "Starting", "Stopped", "Stopping", "Unknown" -> cgmStatusText
        else -> when {
            !Strings.isNullOrEmpty(cgmStatusText) -> cgmStatusText
            else -> when {
                cgmReading != null && cgmDeltaArrow != null -> "${cgmReading}"
                cgmReading != null -> "${cgmReading}"
                else -> ""
            }
        }
    }
    val primaryColor = when (cgmHighLowState) {
        "HIGH" -> redTheme.colors.secondary
        "LOW" -> redTheme.colors.primary
        else -> defaultTheme.colors.primary
    }

    curvedText(
        modifier = CurvedModifier
            .background(Color.Transparent),
        color = primaryColor,
        text = displayText ?: "",
        style = textStyle ?: CurvedTextStyle(),
    )
}