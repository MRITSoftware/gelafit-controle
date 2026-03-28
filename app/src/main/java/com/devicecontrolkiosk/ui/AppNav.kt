package com.devicecontrolkiosk.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.devicecontrolkiosk.data.DeviceConfigStore
import com.devicecontrolkiosk.ui.screens.OnboardingScreen
import com.devicecontrolkiosk.ui.screens.StatusScreen

@Composable
fun AppNav() {
    val context = LocalContext.current
    val navController: NavHostController = rememberNavController()
    val startDestination = if (DeviceConfigStore.isRegistered(context)) "status" else "onboarding"
    NavHost(navController = navController, startDestination = startDestination) {
        composable("onboarding") { OnboardingScreen(navController) }
        composable("status") { StatusScreen(navController) }
    }
}
