package com.devicecontrolkiosk.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.devicecontrolkiosk.data.DeviceConfigStore
import com.devicecontrolkiosk.data.SupabaseApi
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun OnboardingScreen(navController: NavController) {
    val unitEmail = remember { mutableStateOf(TextFieldValue()) }
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
            value = unitEmail.value,
            onValueChange = { unitEmail.value = it },
            label = { Text("E-mail da unidade") },
            supportingText = { Text("Se esse e-mail ja existir, o cadastro atual sera sobrescrito.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = !isLoading.value
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                val normalizedEmail = unitEmail.value.text.trim().lowercase()
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
                    errorMsg.value = "Informe um e-mail valido da unidade."
                    return@Button
                }

                isLoading.value = true
                errorMsg.value = null
                val uuid = UUID.randomUUID().toString()
                scope.launch {
                    val success = SupabaseApi.registerDevice(uuid, normalizedEmail)
                    isLoading.value = false
                    if (success) {
                        DeviceConfigStore.saveRegistration(context, uuid, normalizedEmail)
                        navController.navigate("status") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    } else {
                        errorMsg.value = "Erro ao registrar ou atualizar a unidade."
                    }
                }
            },
            enabled = !isLoading.value
        ) {
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
