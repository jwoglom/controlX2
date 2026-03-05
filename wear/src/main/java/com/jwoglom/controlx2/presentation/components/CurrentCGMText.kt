package com.jwoglom.controlx2.presentation.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
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
import com.jwoglom.controlx2.presentation.defaultTheme
import com.jwoglom.controlx2.presentation.redTheme
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.util.GlucoseConverter

@Composable
fun CurrentCGMText(
    cgmSessionState: String?,
    cgmStatusText: String?,
    cgmReading: Int?,
    cgmDeltaArrow: String?,
    cgmHighLowState: String?,
    glucoseUnit: GlucoseUnit = GlucoseUnit.MGDL,
    textStyle: TextStyle? = null,
) {
    val displayText = when (cgmSessionState) {
        "Starting", "Stopped", "Stopping", "Unknown" -> cgmStatusText!!
        else -> when {
            !Strings.isNullOrEmpty(cgmStatusText) -> cgmStatusText!!
            else -> when {
                cgmReading != null && cgmDeltaArrow != null -> "${GlucoseConverter.format(cgmReading, glucoseUnit)} ${cgmDeltaArrow}"
                cgmReading != null -> GlucoseConverter.format(cgmReading, glucoseUnit)
                else -> ""
            }
        }
    }
    val primaryColor = when (cgmHighLowState) {
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
    cgmSessionState: String?,
    cgmStatusText: String?,
    cgmDeltaArrow: String?,
    cgmHighLowState: String?,
    textStyle: TextStyle? = null,
    modifier: Modifier = Modifier,
) {
    val displayText = when (cgmSessionState) {
        "Starting", "Stopped", "Stopping", "Unknown" -> ""
        else -> when {
            !Strings.isNullOrEmpty(cgmStatusText) -> ""
            else -> when {
                cgmDeltaArrow != null -> "${cgmDeltaArrow}"
                else -> ""
            }
        }
    }
    val primaryColor = when (cgmHighLowState) {
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
    glucoseUnit: GlucoseUnit = GlucoseUnit.MGDL,
) {
    val formattedReading = cgmReading?.let { GlucoseConverter.format(it, glucoseUnit) }
    val displayText = when (cgmSessionState) {
        "Starting", "Stopped", "Stopping", "Unknown" -> cgmStatusText
        else -> when {
            !Strings.isNullOrEmpty(cgmStatusText) -> cgmStatusText
            else -> when {
                formattedReading != null && cgmDeltaArrow != null -> formattedReading
                formattedReading != null -> formattedReading
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