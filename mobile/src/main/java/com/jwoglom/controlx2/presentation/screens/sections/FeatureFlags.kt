@file:OptIn(ExperimentalMaterial3Api::class)

package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jwoglom.controlx2.presentation.components.HeaderLine
import com.jwoglom.controlx2.shared.FeatureFlag

@Composable
fun FeatureFlags(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    navigateBack: () -> Unit,
) {
    val context = LocalContext.current

    LazyColumn(
        contentPadding = innerPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 0.dp),
        content = {
            item {
                ListItem(
                    headlineContent = { Text("Back") },
                    leadingContent = { Icon(Icons.Filled.ArrowBack, contentDescription = null) },
                    modifier = Modifier.clickable { navigateBack() },
                )
                HeaderLine("Feature Flags")
                Divider()
            }

            FeatureFlag.values().forEach { flag ->
                item {
                    var enabled by remember { mutableStateOf(FeatureFlag.enabled(context, flag)) }
                    ListItem(
                        headlineContent = { Text(flag.slug) },
                        trailingContent = {
                            Switch(
                                checked = enabled,
                                onCheckedChange = {
                                    enabled = it
                                    FeatureFlag.set(context, flag, it)
                                },
                            )
                        },
                        modifier = Modifier.clickable {
                            enabled = !enabled
                            FeatureFlag.set(context, flag, enabled)
                        },
                    )
                    Divider()
                }
            }
        }
    )
}
