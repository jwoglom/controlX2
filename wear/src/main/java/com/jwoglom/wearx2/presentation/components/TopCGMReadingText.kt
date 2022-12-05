package com.jwoglom.wearx2.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.MutableLiveData
import androidx.wear.compose.foundation.AnchorType
import androidx.wear.compose.foundation.CurvedDirection
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedModifier
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.foundation.curvedRow
import androidx.wear.compose.foundation.radialGradientBackground
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.curvedText
import com.google.common.base.Strings
import com.jwoglom.wearx2.LocalDataStore
import com.jwoglom.wearx2.presentation.redTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopCGMReadingText() {
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

    Row(
        //modifier = Modifier.fillMaxSize(),
        modifier = Modifier.nestedScroll(
            TopAppBarDefaults.enterAlwaysScrollBehavior(
                rememberTopAppBarState()
            ).nestedScrollConnection).fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            modifier = Modifier
                .padding(top = 2.dp)
                .background(Color.Transparent),
            textAlign = TextAlign.Center,
            color = primaryColor,
            text = displayText,
            fontSize = 18.sp
        )

    }
}