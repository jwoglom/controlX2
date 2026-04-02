package com.jwoglom.controlx2.presentation.screens.sections.components.cartridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme

data class WizardStepInfo(
    val currentStep: Int,
    val totalSteps: Int,
)

@Composable
fun CartridgeWorkflowScreen(
    title: String,
    innerPadding: PaddingValues = PaddingValues(),
    stepInfo: WizardStepInfo? = null,
    canCancel: Boolean = true,
    onCancel: () -> Unit,
    body: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Normal)
            TextButton(onClick = onCancel, enabled = canCancel) {
                Text("Cancel")
            }
        }

        stepInfo?.let {
            Text(
                "Step ${it.currentStep} of ${it.totalSteps}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { it.currentStep.toFloat() / it.totalSteps.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Divider()

        Spacer(modifier = Modifier.height(24.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            content = body,
        )

        Spacer(modifier = Modifier.height(16.dp))
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

@Composable
fun PrimaryActionButton(
    text: String,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        if (loading) {
            Text("Working...")
        } else {
            Text(text)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CartridgeWorkflowScreenPreview() {
    ControlX2Theme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            CartridgeWorkflowScreen(
                title = "Preview Cartridge",
                onCancel = {},
                body = {
                    Text("Status", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Example body content", style = MaterialTheme.typography.bodyLarge)
                },
                actions = {
                    PrimaryActionButton("Continue", onClick = {})
                }
            )
        }
    }
}
