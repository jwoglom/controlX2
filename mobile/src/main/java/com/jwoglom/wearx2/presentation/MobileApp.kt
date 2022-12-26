@file:OptIn(ExperimentalMaterialApi::class)

package com.jwoglom.wearx2.presentation

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.wearx2.presentation.navigation.Screen
import com.jwoglom.wearx2.presentation.screens.AppSetup
import com.jwoglom.wearx2.presentation.screens.FirstLaunch
import com.jwoglom.wearx2.presentation.screens.Landing
import com.jwoglom.wearx2.presentation.screens.PumpSetup
import com.jwoglom.wearx2.presentation.theme.WearX2Theme
import com.jwoglom.wearx2.shared.util.SendType

@Composable
fun MobileApp(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.FirstLaunch.route,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
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

            composable(Screen.Landing.route) {
                Landing(
                    navController = navController,
                    sendMessage = sendMessage,
                    sendPumpCommands = sendPumpCommands,
                )
            }
        }

        LaunchedEffect (Unit) {
            navController.navigate(startDestination)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MobileApp(
        startDestination = Screen.FirstLaunch.route,
        sendMessage = {_, _ -> },
        sendPumpCommands = {_, _ -> }
    )
}