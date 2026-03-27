package com.devicecontrolkiosk.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.devicecontrolkiosk.ui.screens.OnboardingScreen
import com.devicecontrolkiosk.ui.screens.StatusScreen

@Composable
fun AppNav() {
    val navController: NavHostController = rememberNavController()
    NavHost(navController = navController, startDestination = "onboarding") {
        composable("onboarding") { OnboardingScreen(navController) }
        composable("status") { StatusScreen(navController) }
    }
}
