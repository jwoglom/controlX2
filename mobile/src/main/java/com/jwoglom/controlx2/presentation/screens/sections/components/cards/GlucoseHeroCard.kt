package com.jwoglom.controlx2.presentation.screens.sections.components.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.presentation.screens.sections.components.toCgmDataPoint
import com.jwoglom.controlx2.presentation.theme.CardBackground
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.theme.Elevation
import com.jwoglom.controlx2.presentation.theme.GlucoseColors
import com.jwoglom.controlx2.presentation.theme.Spacing
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.util.GlucoseConverter

@Composable
fun GlucoseHeroCard(
    glucoseValue: Int?,
    deltaArrow: String?,
    modifier: Modifier = Modifier,
    // Variacion respecto a la lectura anterior, en mg/dL (null = no mostrar)
    deltaMgdl: Int? = null,
    // Minutos desde la lectura (null = no mostrar)
    ageMinutes: Int? = null
) {
    val dataStore = LocalDataStore.current
    val glucoseUnit by dataStore.glucoseUnitPreference.observeAsState(GlucoseUnit.MGDL)

    // 0 value == no CGM connected
    val noCgmConnected = (glucoseValue == 0)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Small, vertical = Spacing.Small),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.Card),
        shape = RoundedCornerShape(Spacing.CardCornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(Spacing.CardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Label
//            Text(
//                if (noCgmConnected)
//                    "No CGM Connected"
//                else
//                    "Current Glucose",
//                style = MaterialTheme.typography.titleMedium,
//                color = MaterialTheme.colorScheme.onSurfaceVariant
//            )
            // Hero number with color coding and trend arrow
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val displayValue = if (!noCgmConnected && glucoseValue != null) {
                    when (glucoseUnit) {
                        GlucoseUnit.MGDL -> glucoseValue.toString()
                        GlucoseUnit.MMOL -> String.format("%.1f", glucoseValue * GlucoseConverter.MGDL_TO_MMOL_FACTOR)
                    }
                } else if (!noCgmConnected) {
                    "--"
                } else {
                    "n/a"
                }

                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (noCgmConnected) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        getGlucoseColor(glucoseValue)
                    }
                )

                if (!deltaArrow.isNullOrEmpty()) {
                    // Trend arrow
                    Text(
                        text = deltaArrow,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (noCgmConnected) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            getGlucoseColor(glucoseValue)
                        }
                    )
                }

            }

            // Unit label + variacion respecto a la lectura anterior
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (!noCgmConnected && ageMinutes != null) {
                    Text(
                        "${ageMinutes}min ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!noCgmConnected && deltaMgdl != null) {
                    val deltaText = when (glucoseUnit) {
                        GlucoseUnit.MGDL -> String.format("%+d", deltaMgdl)
                        GlucoseUnit.MMOL -> String.format("%+.1f", deltaMgdl * GlucoseConverter.MGDL_TO_MMOL_FACTOR)
                    }
                    Text(
                        "$deltaText ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    glucoseUnit.abbreviation,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Variacion en mg/dL entre la lectura actual y la anterior, a partir del historial de la bomba.
 * Devuelve null si no hay dos lecturas cercanas (mas de 12 min entre ellas).
 */
fun cgmDeltaMgdl(logs: List<HistoryLogItem>?, current: Int?, currentReadingSeconds: Long? = null): Int? {
    if (logs == null || current == null || current <= 0) return null
    val points = logs.mapNotNull { it.toCgmDataPoint() }.sortedByDescending { it.timestamp }
    if (points.isEmpty()) return null
    val latest = points[0]
    // Si la lectura actual aun no esta en el historial, se compara con la ultima del historial,
    // pero solo si es la lectura inmediatamente anterior (<= 7 min respecto a la actual). Antes se
    // comparaba con una lectura de hace 10-12 min y salia +21 donde xDrip decia +9.
    if (current != latest.value.toInt()) {
        val ref = currentReadingSeconds ?: (System.currentTimeMillis() / 1000)
        val gap = ref - latest.timestamp
        return if (gap in 0..(7 * 60)) current - latest.value.toInt() else null
    }
    val previous = points.getOrNull(1) ?: return null
    if (latest.timestamp - previous.timestamp > 7 * 60) return null
    return latest.value.toInt() - previous.value.toInt()
}

/**
 * Minutos transcurridos desde la ultima lectura segun el historial de la bomba.
 * Si la lectura actual aun no esta en el historial, se considera recien llegada (0).
 */
fun cgmAgeMinutes(logs: List<HistoryLogItem>?, current: Int?, nowSeconds: Long): Int? {
    if (logs == null || current == null || current <= 0) return null
    val latest = logs.mapNotNull { it.toCgmDataPoint() }.maxByOrNull { it.timestamp } ?: return null
    if (current != latest.value.toInt()) return 0
    return maxOf(0L, (nowSeconds - latest.timestamp) / 60).toInt()
}

@Composable
private fun getGlucoseColor(glucose: Int?): Color {
    return when (glucose) {
        null, 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        in 1..69 -> GlucoseColors.Severe
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
