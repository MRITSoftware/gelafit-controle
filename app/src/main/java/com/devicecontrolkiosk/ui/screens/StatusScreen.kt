package com.devicecontrolkiosk.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.devicecontrolkiosk.data.DeviceConfigStore
import com.devicecontrolkiosk.data.SupabaseApi
import com.devicecontrolkiosk.service.CommandService
import kotlinx.coroutines.launch

data class InstalledApp(
    val label: String,
    val packageName: String
)

@Composable
fun StatusScreen(navController: NavController) {
    val context = navController.context
    val config = remember { DeviceConfigStore.getConfig(context) }
    var installedApps by remember { mutableStateOf(emptyList<InstalledApp>()) }
    var selectedPackages by remember { mutableStateOf(config.controlledPackages) }
    var kioskPackage by remember { mutableStateOf(config.kioskPackage) }
    var statusMessage by remember { mutableStateOf("Monitorando comandos remotos") }
    var selectionError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val powerManager = remember { context.getSystemService(PowerManager::class.java) }
    var notificationsGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var ignoringBatteryOptimizations by remember {
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
        if (!granted) {
            statusMessage = "Permissao de notificacao negada. O servico pode ficar oculto."
        }
    }

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
                Text("Permissoes e acesso", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Notificacao: ${if (notificationsGranted) "ok" else "pendente"} | Bateria: ${if (ignoringBatteryOptimizations) "liberado" else "restrito"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted
                    ) {
                        Text("Pedir notificacao")
                    }
                    Button(
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        },
                        enabled = !ignoringBatteryOptimizations
                    ) {
                        Text("Liberar bateria")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Selecione exatamente 2 apps", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Escolhidos: ${selectedPackages.size}/2. O app marcado como quiosque sera aberto por ultimo no boot e a escolha sera espelhada no Supabase.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (selectedPackages.isNotEmpty()) {
                    Text("Ordem atual: ${selectedPackages.joinToString(" -> ")}", style = MaterialTheme.typography.bodySmall)
                }
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
                                        !checked -> selectedPackages.filterNot { it == app.packageName }
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start
                            ) {
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
                        if (isSelected) {
                            Text(
                                "Posicao: ${selectedPackages.indexOf(app.packageName) + 1}",
                                style = MaterialTheme.typography.bodySmall
                            )
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
                        statusMessage = "Configuracao salva. Sincronizando modo kiosk no Supabase."
                        selectionError = null
                        scope.launch {
                            val deviceId = DeviceConfigStore.getConfig(context).deviceId
                            val synced = if (!deviceId.isNullOrBlank()) {
                                SupabaseApi.syncKioskModes(deviceId, selectedPackages, kioskPackage)
                            } else {
                                false
                            }
                            statusMessage = if (synced) {
                                "Configuracao salva e tabela remota atualizada."
                            } else {
                                "Configuracao salva localmente, mas a tabela remota nao foi atualizada."
                            }
                            ContextCompat.startForegroundService(
                                context,
                                Intent(context, CommandService::class.java).putExtra(CommandService.EXTRA_TRIGGER_LAUNCH, true)
                            )
                        }
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

private fun hasNotificationPermission(context: android.content.Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
}
