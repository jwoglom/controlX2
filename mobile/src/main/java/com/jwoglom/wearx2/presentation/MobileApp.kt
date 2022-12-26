@file:OptIn(ExperimentalMaterialApi::class)

package com.jwoglom.wearx2.presentation

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jwoglom.wearx2.presentation.navigation.Screen
import com.jwoglom.wearx2.presentation.screens.AppSetup
import com.jwoglom.wearx2.presentation.screens.FirstLaunch
import com.jwoglom.wearx2.presentation.screens.Landing
import com.jwoglom.wearx2.presentation.screens.PumpSetup
import com.jwoglom.wearx2.presentation.theme.WearX2Theme

@Composable
fun MobileApp(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.FirstLaunch.route,
    sendMessage: (String, ByteArray) -> Unit,
) {
    WearX2Theme {
        NavHost(
            navController = navController,
            startDestination = startDestination,
        ) {
            composable(Screen.FirstLaunch.route) {
                FirstLaunch(
                    navController = navController,
                    sendMessage = sendMessage,
                )
            }

            composable(Screen.PumpSetup.route) {
                PumpSetup(
                    navController = navController,
                    sendMessage = sendMessage,
                )
            }

            composable(Screen.AppSetup.route) {
                AppSetup(
                    navController = navController,
                    sendMessage = sendMessage,
                )
            }

            composable(Screen.AppSetup.route) {
                Landing(
                    navController = navController,
                    sendMessage = sendMessage,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MobileApp(
        startDestination = Screen.FirstLaunch.route,
        sendMessage = {_, _ -> },
    )
}