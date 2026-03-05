package com.jwoglom.controlx2.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.jwoglom.controlx2.BuildConfig
import hu.supercluster.paperwork.Paperwork

@Composable
fun LandingBuildInfo() {
    val context = LocalContext.current
    val p = Paperwork(context)
    Text(
        buildAnnotatedString {
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp)) {
                append("ControlX2 ")
                append(BuildConfig.VERSION_NAME)
            }
            append("\n")
            append("with PumpX2 ")
            append(com.jwoglom.pumpx2.BuildConfig.PUMPX2_VERSION)
            append("\n")
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Build: ") }
            append(p.get("build_version"))
            append("\n")
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Build time: ") }
            append(p.get("build_time"))
            append("\n")
        },
        color = Color.White,
        fontSize = 8.sp,
    )
}

@Preview
@Composable
private fun LandingBuildInfoPreview() {
    LandingBuildInfo()
}
