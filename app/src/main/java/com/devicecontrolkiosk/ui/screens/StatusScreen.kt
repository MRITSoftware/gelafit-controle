package com.devicecontrolkiosk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
@Composable
fun StatusScreen(navController: NavController) {
    val context = navController.context
    val prefs = remember { context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE) }
    val deviceId = prefs.getString("device_id", "N/A")
    var pollingStatus by remember { mutableStateOf("Monitorando comandos...") }

    // Simulação de polling visual
    LaunchedEffect(Unit) {
        while (true) {
            pollingStatus = "Monitorando comandos... (${System.currentTimeMillis()/1000})"
            kotlinx.coroutines.delay(2000)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Status do Dispositivo", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Device ID: $deviceId")
        Spacer(modifier = Modifier.height(16.dp))
        Text(pollingStatus)
    }
}
