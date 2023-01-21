package com.jwoglom.controlx2.presentation.navigation

sealed class Screen(
    val route: String
) {
    object FirstLaunch : Screen("FirstLaunch")
    object PumpSetup : Screen("PumpSetup")
    object AppSetup : Screen("AppSetup")
    object Landing : Screen("Landing")
}
