package com.devicecontrolkiosk.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.devicecontrolkiosk.data.DeviceConfigStore
import com.devicecontrolkiosk.data.SupabaseApi
import com.devicecontrolkiosk.kiosk.KioskManager
import com.devicecontrolkiosk.service.CommandService
import kotlinx.coroutines.launch

data class InstalledApp(
    val label: String,
    val packageName: String
)

@Composable
fun StatusScreen(navController: NavController) {
    val context = navController.context
    val lifecycleOwner = LocalLifecycleOwner.current
    var config by remember { mutableStateOf(DeviceConfigStore.getConfig(context)) }
    var installedApps by remember { mutableStateOf(emptyList<InstalledApp>()) }
    var selectedPackages by remember { mutableStateOf(config.controlledPackages) }
    var kioskPackage by remember { mutableStateOf(config.kioskPackage) }
    var statusMessage by remember { mutableStateOf("Monitorando comandos remotos e kiosk") }
    var selectionError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val powerManager = remember { context.getSystemService(PowerManager::class.java) }
    var notificationsGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var ignoringBatteryOptimizations by remember {
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true)
    }
    var usageAccessGranted by remember { mutableStateOf(KioskManager.canMonitorForeground(context)) }
    var deviceAdminActive by remember { mutableStateOf(KioskManager.isAdminActive(context)) }
    var deviceOwnerActive by remember { mutableStateOf(KioskManager.isDeviceOwner(context)) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
        if (!granted) {
            statusMessage = "Permissao de notificacao negada. O servico pode ficar oculto."
        }
    }

    fun refreshAccessState() {
        config = DeviceConfigStore.getConfig(context)
        notificationsGranted = hasNotificationPermission(context)
        ignoringBatteryOptimizations = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        usageAccessGranted = KioskManager.canMonitorForeground(context)
        deviceAdminActive = KioskManager.isAdminActive(context)
        deviceOwnerActive = KioskManager.isDeviceOwner(context)
    }

    val pendingAccessActions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted) {
            add(AccessAction(
                label = "Pedir notificacao",
                onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            ))
        }
        if (!ignoringBatteryOptimizations) {
            add(AccessAction(
                label = "Liberar bateria",
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            ))
        }
        if (!usageAccessGranted) {
            add(AccessAction(
                label = "Liberar uso",
                onClick = { KioskManager.openUsageAccessSettings(context) }
            ))
        }
        if (!deviceAdminActive) {
            add(AccessAction(
                label = "Ativar admin",
                onClick = { context.startActivity(KioskManager.buildAddDeviceAdminIntent(context)) }
            ))
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshAccessState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                Text(
                    "Uso: ${if (usageAccessGranted) "ok" else "pendente"} | Admin: ${if (deviceAdminActive) "ok" else "pendente"} | Device Owner: ${if (deviceOwnerActive) "ok" else "pendente"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    if (deviceOwnerActive) {
                        "Modo kiosk real e reboot remoto habilitados."
                    } else {
                        "Sem Device Owner o app ainda força o retorno do kiosk, mas o lock task real depende dessa configuracao."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                if (pendingAccessActions.isEmpty()) {
                    Text(
                        "Todos os acessos principais ja foram liberados.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        "Acoes pendentes: ${pendingAccessActions.joinToString { it.label }}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    pendingAccessActions.forEach { action ->
                        Button(
                            onClick = action.onClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(action.label)
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Selecione exatamente 2 apps", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Escolhidos: ${selectedPackages.size}/2. O app marcado como quiosque sera aberto por ultimo no boot e restaurado se sair do foco.",
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
            if (selectedPackages.isNotEmpty()) {
                item(key = "quick_actions") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Acoes rapidas", style = MaterialTheme.typography.titleMedium)
                            selectedPackages.forEachIndexed { index, packageName ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("App ${index + 1}: $packageName", modifier = Modifier.weight(1f))
                                    Button(
                                        onClick = {
                                            ContextCompat.startForegroundService(
                                                context,
                                                Intent(context, CommandService::class.java).apply {
                                                    action = CommandService.ACTION_RESTART_PACKAGE
                                                    putExtra(CommandService.EXTRA_PACKAGE_NAME, packageName)
                                                }
                                            )
                                            statusMessage = "Reinicio solicitado para $packageName"
                                        }
                                    ) {
                                        Text("Reiniciar")
                                    }
                                }
                            }
                            Button(
                                onClick = {
                                    ContextCompat.startForegroundService(
                                        context,
                                        Intent(context, CommandService::class.java).apply {
                                            action = CommandService.ACTION_ENSURE_KIOSK
                                        }
                                    )
                                    statusMessage = "Kiosk reforcado manualmente."
                                },
                                enabled = kioskPackage != null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Ativar kiosk agora")
                            }
                        }
                    }
                }
            }

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
                        config = DeviceConfigStore.getConfig(context)
                        statusMessage = "Configuracao salva. Iniciando apps e sincronizando kiosk."
                        selectionError = null
                        scope.launch {
                            val deviceId = DeviceConfigStore.getConfig(context).deviceId
                            val synced = if (!deviceId.isNullOrBlank()) {
                                SupabaseApi.syncKioskModes(deviceId, selectedPackages, kioskPackage)
                            } else {
                                false
                            }
                            statusMessage = if (synced) {
                                "Configuracao salva, apps iniciados e tabela remota atualizada."
                            } else {
                                "Configuracao salva e apps iniciados, mas a tabela remota nao foi atualizada."
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

private data class AccessAction(
    val label: String,
    val onClick: () -> Unit
)

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
