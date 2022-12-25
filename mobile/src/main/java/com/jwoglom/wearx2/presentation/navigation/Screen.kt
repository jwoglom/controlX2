package com.jwoglom.wearx2.presentation.navigation

sealed class Screen(
    val route: String
) {
    object FirstLaunch : Screen("FirstLaunch")
}
