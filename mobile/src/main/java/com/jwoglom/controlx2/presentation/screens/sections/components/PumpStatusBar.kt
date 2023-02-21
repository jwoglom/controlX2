package com.jwoglom.controlx2.presentation.screens.sections.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.presentation.screens.setUpPreviewState
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme

@Composable
fun PumpStatusBar() {
    val ds = LocalDataStore.current

    LazyRow {
        item {
            val batteryPercent = ds.batteryPercent.observeAsState()
            HorizBatteryIcon(batteryPercent.value)
        }
    }

}



@Preview(showBackground = true)
@Composable
private fun DefaultPreview() {
    ControlX2Theme() {
        Surface(
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            PumpStatusBar()
        }
    }
}