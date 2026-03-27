package com.devicecontrolkiosk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.devicecontrolkiosk.data.SupabaseApi
import java.util.UUID
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun OnboardingScreen(navController: NavController) {
    val deviceName = remember { mutableStateOf(TextFieldValue()) }
    val isLoading = remember { mutableStateOf(false) }
    val errorMsg = remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = navController.context

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Registrar Dispositivo", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = deviceName.value,
            onValueChange = { deviceName.value = it },
            label = { Text("Nome da Unidade") },
            enabled = !isLoading.value
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = {
            isLoading.value = true
            errorMsg.value = null
            val uuid = UUID.randomUUID().toString()
            scope.launch {
                val success = SupabaseApi.registerDevice(uuid, deviceName.value.text.ifBlank { null })
                isLoading.value = false
                if (success) {
                    // Salva device_id para uso pelo serviço
                    val prefs = context.getSharedPreferences("device_prefs", android.content.Context.MODE_PRIVATE)
                    prefs.edit().putString("device_id", uuid).apply()
                    navController.navigate("status") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                } else {
                    errorMsg.value = "Erro ao registrar. Tente novamente."
                }
            }
        }, enabled = !isLoading.value) {
            if (isLoading.value) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Registrar")
            }
        }
        errorMsg.value?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
