package com.jwoglom.controlx2.presentation.screens.sections.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jwoglom.controlx2.presentation.screens.sections.Settings
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.util.AppVersionCheck
import com.jwoglom.controlx2.util.AppVersionInfo

@Composable
fun VersionInfo(
    context: Context
) {
    val ver = AppVersionInfo(context)
    Text(buildAnnotatedString {
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)) {
            append("ControlX2 ")
            append(ver.version)
        }
    }, Modifier.padding(start = 16.dp, top = 16.dp))
    Text(buildAnnotatedString {
        append("with PumpX2 ")
        append(ver.pumpX2)
        append("\n")

        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
            append("Build: ")
        }
        append(ver.buildVersion)
        append("\n")

        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
            append("Build time: ")
        }
        append(ver.buildTime)
        append("\n")

        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
            append("PumpX2 build time: ")
        }
        append(ver.pumpX2BuildTime)
        append("\n")
    }, lineHeight = 20.sp, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp).clickable {
        Toast.makeText(context, "Checking for version update", Toast.LENGTH_SHORT).show()
        AppVersionCheck(context)
    })
}



@Preview(showBackground = true)
@Composable
private fun DefaultPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            VersionInfo(LocalContext.current)
        }
    }
}