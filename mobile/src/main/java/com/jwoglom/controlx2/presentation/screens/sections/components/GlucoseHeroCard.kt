package com.jwoglom.controlx2.presentation.screens.sections.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.presentation.theme.CardBackground
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.theme.Elevation
import com.jwoglom.controlx2.presentation.theme.GlucoseColors
import com.jwoglom.controlx2.presentation.theme.Spacing

@Composable
fun GlucoseHeroCard(
    glucoseValue: Int?,
    deltaArrow: String?,
    modifier: Modifier = Modifier
) {
    // 0 value == no CGM connected
    val noCgmConnected = (glucoseValue == 0)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.Card),
        shape = RoundedCornerShape(Spacing.CardCornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.CardPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Label
            Text(
                if (noCgmConnected)
                    "No CGM Connected"
                else
                    "Current Glucose",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!noCgmConnected) {

                Spacer(Modifier.height(Spacing.Small))

                // Hero number with color coding and trend arrow
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = glucoseValue?.toString() ?: "--",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = getGlucoseColor(glucoseValue)
                    )

                    if (!deltaArrow.isNullOrEmpty()) {
                        Spacer(Modifier.width(Spacing.Medium))

                        // Trend arrow
                        Text(
                            text = deltaArrow,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = getGlucoseColor(glucoseValue)
                        )
                    }
                }

                // Unit label
                Text(
                    "mg/dL",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun getGlucoseColor(glucose: Int?): Color {
    return when (glucose) {
        null -> MaterialTheme.colorScheme.onSurfaceVariant
        in 0..69 -> GlucoseColors.Severe
        in 70..79 -> GlucoseColors.Low
        in 80..180 -> GlucoseColors.InRange
        in 181..250 -> GlucoseColors.Elevated
        else -> GlucoseColors.High
    }
}

@Preview(showBackground = true)
@Composable
internal fun GlucoseHeroCardInRangePreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFFAFAFA)
        ) {
            GlucoseHeroCard(
                glucoseValue = 120,
                deltaArrow = "→"
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun GlucoseHeroCardHighPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFFAFAFA)
        ) {
            GlucoseHeroCard(
                glucoseValue = 280,
                deltaArrow = "↑"
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun GlucoseHeroCardLowPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFFAFAFA)
        ) {
            GlucoseHeroCard(
                glucoseValue = 65,
                deltaArrow = "↓"
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun GlucoseHeroCardElevatedPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFFAFAFA)
        ) {
            GlucoseHeroCard(
                glucoseValue = 200,
                deltaArrow = "↗"
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun GlucoseHeroCardNullPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFFAFAFA)
        ) {
            GlucoseHeroCard(
                glucoseValue = null,
                deltaArrow = null
            )
        }
    }
}
