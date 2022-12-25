package com.jwoglom.wearx2.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jwoglom.wearx2.presentation.navigation.Screen
import com.jwoglom.wearx2.presentation.screens.FirstLaunch
import com.jwoglom.wearx2.presentation.screens.InitialSetup
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

            composable(Screen.InitialSetup.route) {
                InitialSetup(
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