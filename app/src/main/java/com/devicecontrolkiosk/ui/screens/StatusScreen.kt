package com.devicecontrolkiosk.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.devicecontrolkiosk.data.DeviceConfigStore
import com.devicecontrolkiosk.service.CommandService

data class InstalledApp(
    val label: String,
    val packageName: String
)

@Composable
fun StatusScreen(navController: NavController) {
    val context = navController.context
    val config = remember { DeviceConfigStore.getConfig(context) }
    var installedApps by remember { mutableStateOf(emptyList<InstalledApp>()) }
    var selectedPackages by remember { mutableStateOf(config.controlledPackages.toSet()) }
    var kioskPackage by remember { mutableStateOf(config.kioskPackage) }
    var statusMessage by remember { mutableStateOf("Monitorando comandos remotos") }
    var selectionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        installedApps = loadLaunchableApps(context.packageManager, context.packageName)
        if (selectedPackages.size != 2) {
            statusMessage = "Escolha 2 apps para controle remoto."
        }
        ContextCompat.startForegroundService(context, Intent(context, CommandService::class.java))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Status do Dispositivo", style = MaterialTheme.typography.headlineMedium)
        Text("Device ID: ${config.deviceId ?: "N/A"}")
        Text("Unidade: ${config.unitEmail ?: "N/A"}")
        Text(statusMessage, color = MaterialTheme.colorScheme.primary)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Selecione exatamente 2 apps", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Escolhidos: ${selectedPackages.size}/2. O app marcado como quiosque sera aberto por ultimo no boot.",
                    style = MaterialTheme.typography.bodyMedium
                )
                selectionError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(installedApps, key = { it.packageName }) { app ->
                val isSelected = selectedPackages.contains(app.packageName)
                val canSelect = isSelected || selectedPackages.size < 2
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.titleMedium)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                            }
                            Checkbox(
                                checked = isSelected,
                                enabled = canSelect,
                                onCheckedChange = { checked ->
                                    selectionError = null
                                    selectedPackages = when {
                                        checked && selectedPackages.size < 2 -> selectedPackages + app.packageName
                                        !checked -> selectedPackages - app.packageName
                                        else -> {
                                            selectionError = "Voce pode controlar apenas 2 apps."
                                            selectedPackages
                                        }
                                    }
                                    if (!selectedPackages.contains(kioskPackage)) {
                                        kioskPackage = null
                                    }
                                }
                            )
                        }
                        if (isSelected) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = kioskPackage == app.packageName,
                                    onClick = {
                                        kioskPackage = app.packageName
                                        selectionError = null
                                    }
                                )
                                Text("Definir como app de quiosque")
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                when {
                    selectedPackages.size != 2 -> selectionError = "Selecione exatamente 2 apps."
                    kioskPackage == null -> selectionError = "Escolha qual dos 2 apps sera o quiosque."
                    else -> {
                        DeviceConfigStore.saveAppSelection(context, selectedPackages.toList(), kioskPackage)
                        statusMessage = "Configuracao salva. Iniciando apps controlados."
                        selectionError = null
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, CommandService::class.java).putExtra(CommandService.EXTRA_TRIGGER_LAUNCH, true)
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Salvar configuracao e iniciar apps")
        }
    }
}

private fun loadLaunchableApps(packageManager: PackageManager, currentPackage: String): List<InstalledApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return packageManager.queryIntentActivities(intent, 0)
        .mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
            if (packageName == currentPackage) {
                return@mapNotNull null
            }
            InstalledApp(
                label = resolveInfo.loadLabel(packageManager).toString(),
                packageName = packageName
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}
