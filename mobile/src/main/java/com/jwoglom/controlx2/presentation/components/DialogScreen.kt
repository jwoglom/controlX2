package com.jwoglom.controlx2.presentation.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.presentation.theme.Colors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogScreen(
    title: String,
    buttonContent: @Composable RowScope.() -> Unit,
    actionContent: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(title)
                },
                actions = actionContent,
                colors = if (isSystemInDarkTheme()) TopAppBarDefaults.topAppBarColors(containerColor = Colors.primary, titleContentColor = Color.Black)
                    else TopAppBarDefaults.topAppBarColors(containerColor = Colors.primary)
            )
        },
        content = { innerPadding ->
            LazyColumn(
                contentPadding = innerPadding,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
                content = content
            )
        },
        bottomBar = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                content = buttonContent
            )
        }
    )
}