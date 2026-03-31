package com.devicecontrolkiosk.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.devicecontrolkiosk.R
import com.devicecontrolkiosk.data.DeviceConfigStore
import com.devicecontrolkiosk.data.SupabaseApi
import com.devicecontrolkiosk.kiosk.KioskManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

class CommandService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    private var kioskWatchdogJob: Job? = null
    @Volatile private var kioskGraceUntilMs: Long = 0L
    @Volatile private var allowSelfRestart: Boolean = true

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Controle remoto e kiosk ativos"))
        allowSelfRestart = true

        when (intent?.action) {
            ACTION_RESTART_PACKAGE -> {
                intent.getStringExtra(EXTRA_PACKAGE_NAME)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { packageName ->
                        serviceScope.launch {
                            restartApp(packageName, preserveKiosk = true)
                        }
                    }
            }
            ACTION_ENSURE_KIOSK -> {
                serviceScope.launch {
                    enforceKioskIfNeeded(force = true)
                }
            }
        }

        if (intent?.getBooleanExtra(EXTRA_TRIGGER_LAUNCH, false) == true) {
            serviceScope.launch {
                launchConfiguredApps()
            }
        }

        ensurePollingLoop()
        ensureKioskWatchdogLoop()
        return START_STICKY
    }

    private fun ensurePollingLoop() {
        if (pollingJob != null) {
            return
        }

        pollingJob = serviceScope.launch {
            val config = DeviceConfigStore.getConfig(this@CommandService)
            val deviceId = config.deviceId
            if (deviceId.isNullOrBlank()) {
                Log.e("CommandService", "Device ID nao encontrado. Servico nao pode iniciar polling.")
                allowSelfRestart = false
                stopSelf()
                return@launch
            }

            while (isActive) {
                try {
                    val commands = SupabaseApi.pollCommands(deviceId)
                    for (cmd in commands) {
                        processCommand(cmd.command)
                        val marked = SupabaseApi.markCommandExecuted(cmd.id)
                        if (marked) {
                            Log.i("CommandService", "Comando ${cmd.id} marcado como executado.")
                        } else {
                            Log.e("CommandService", "Falha ao marcar comando ${cmd.id} como executado.")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CommandService", "Erro no polling: ${e.message}")
                }
                delay(POLLING_INTERVAL_MS)
            }
        }
    }

    private fun ensureKioskWatchdogLoop() {
        if (kioskWatchdogJob != null) {
            return
        }

        kioskWatchdogJob = serviceScope.launch {
            while (isActive) {
                try {
                    enforceKioskIfNeeded(force = false)
                } catch (e: Exception) {
                    Log.e("CommandService", "Erro no watchdog de kiosk: ${e.message}")
                }
                delay(KIOSK_WATCHDOG_INTERVAL_MS)
            }
        }
    }

    private fun processCommand(commandPayload: String) {
        Log.i("CommandService", "Comando recebido: $commandPayload")
        try {
            val json = JSONObject(commandPayload)
            val type = json.getString("type")
            val payload = json.optJSONObject("payload")
            val config = DeviceConfigStore.getConfig(this)
            when (type) {
                "restart_app" -> payload?.optString("package")?.takeIf { it.isNotBlank() }?.let {
                    serviceScope.launch { restartApp(it, preserveKiosk = true) }
                }
                "restart_first_app" -> config.controlledPackages.firstOrNull()?.let {
                    serviceScope.launch { restartApp(it, preserveKiosk = true) }
                }
                "restart_second_app" -> config.controlledPackages.getOrNull(1)?.let {
                    serviceScope.launch { restartApp(it, preserveKiosk = true) }
                }
                "restart_controlled_apps" -> serviceScope.launch { launchConfiguredApps(restartFirst = true) }
                "set_kiosk" -> payload?.optString("package")?.takeIf { it.isNotBlank() }?.let(::setKioskMode)
                "restart_device" -> restartDevice()
                "set_apps" -> {
                    val apps = payload?.optJSONArray("apps")
                    val kioskPackage = payload?.optString("kiosk_package")?.takeIf { it.isNotBlank() }
                    if (apps != null) {
                        val appList = (0 until apps.length()).map { apps.getString(it) }
                        DeviceConfigStore.saveAppSelection(this, appList, kioskPackage)
                        serviceScope.launch {
                            KioskManager.syncLockTaskPackages(this@CommandService, appList)
                            syncRemoteKioskSelection(appList, kioskPackage)
                            launchConfiguredApps()
                        }
                    }
                }
                else -> Log.w("CommandService", "Tipo de comando desconhecido: $type")
            }
        } catch (e: Exception) {
            Log.e("CommandService", "Erro ao processar comando: ${e.message}")
        }
    }

    private suspend fun launchConfiguredApps(restartFirst: Boolean = false) {
        val config = DeviceConfigStore.getConfig(this)
        if (config.controlledPackages.size != 2) {
            Log.w("CommandService", "Apps controlados ainda nao configurados.")
            return
        }

        KioskManager.syncLockTaskPackages(this, config.controlledPackages)
        val kioskPackage = resolveKioskPackage(config)
        extendKioskGrace(KIOSK_LAUNCH_GRACE_MS)

        if (restartFirst) {
            config.controlledPackages.forEach { packageName ->
                restartApp(packageName, preserveKiosk = false)
                delay(RESTART_DELAY_MS)
            }
            kioskPackage?.let {
                delay(KIOSK_RESTORE_DELAY_MS)
                bringPackageToFront(it, enableKioskLock = true)
            }
            return
        }

        val orderedPackages = buildLaunchOrder(config.controlledPackages, kioskPackage)
        orderedPackages.forEachIndexed { index, packageName ->
            if (index > 0) {
                delay(APP_SWITCH_DELAY_MS)
            }
            bringPackageToFront(packageName, enableKioskLock = packageName == kioskPackage)
        }
    }

    private suspend fun restartApp(packageName: String, preserveKiosk: Boolean) {
        Log.i("CommandService", "Reiniciando app: $packageName")
        try {
            extendKioskGrace(KIOSK_RECOVERY_GRACE_MS)
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(packageName)
            delay(RESTART_DELAY_MS)

            val config = DeviceConfigStore.getConfig(this)
            val kioskPackage = resolveKioskPackage(config)
            bringPackageToFront(packageName, enableKioskLock = packageName == kioskPackage)

            if (preserveKiosk && kioskPackage != null && kioskPackage != packageName) {
                delay(KIOSK_RESTORE_DELAY_MS)
                bringPackageToFront(kioskPackage, enableKioskLock = true)
            }
        } catch (e: Exception) {
            Log.e("CommandService", "Erro ao reiniciar app: ${e.message}")
        }
    }

    private fun buildLaunchOrder(controlledPackages: List<String>, kioskPackage: String?): List<String> {
        val normalized = controlledPackages.distinct().take(2)
        val nonKiosk = normalized.filter { it != kioskPackage }
        return if (kioskPackage != null && normalized.contains(kioskPackage)) {
            nonKiosk + kioskPackage
        } else {
            normalized
        }
    }

    private fun setKioskMode(packageName: String) {
        val config = DeviceConfigStore.getConfig(this)
        DeviceConfigStore.saveAppSelection(this, config.controlledPackages, packageName)
        KioskManager.syncLockTaskPackages(this, config.controlledPackages)
        extendKioskGrace(KIOSK_RECOVERY_GRACE_MS)
        serviceScope.launch {
            syncRemoteKioskSelection(config.controlledPackages, packageName)
        }
        Log.i("CommandService", "App de quiosque definido: $packageName")
        bringPackageToFront(packageName, enableKioskLock = true)
    }

    private suspend fun resolveKioskPackage(config: DeviceConfigStore.AppSelection): String? {
        val remoteKiosk = config.deviceId?.let { deviceId ->
            SupabaseApi.fetchKioskPackage(deviceId, config.controlledPackages)
        }
        if (remoteKiosk != null && remoteKiosk != config.kioskPackage) {
            DeviceConfigStore.saveAppSelection(this, config.controlledPackages, remoteKiosk)
        }
        return remoteKiosk ?: config.kioskPackage
    }

    private suspend fun syncRemoteKioskSelection(controlledPackages: List<String>, kioskPackage: String?) {
        val deviceId = DeviceConfigStore.getConfig(this).deviceId
        if (deviceId.isNullOrBlank()) {
            return
        }
        val synced = SupabaseApi.syncKioskModes(deviceId, controlledPackages, kioskPackage)
        if (!synced) {
            Log.e("CommandService", "Falha ao sincronizar tabela remota de kiosk.")
        }
    }

    private suspend fun enforceKioskIfNeeded(force: Boolean) {
        val config = DeviceConfigStore.getConfig(this)
        val kioskPackage = config.kioskPackage?.takeIf { config.controlledPackages.contains(it) } ?: return

        KioskManager.syncLockTaskPackages(this, config.controlledPackages)

        if (!force && System.currentTimeMillis() < kioskGraceUntilMs) {
            return
        }

        val currentPackage = KioskManager.getForegroundPackage(this) ?: return
        if (currentPackage == kioskPackage || currentPackage == packageName) {
            return
        }

        Log.i("CommandService", "Foreground fora do kiosk detectado: $currentPackage. Restaurando $kioskPackage")
        bringPackageToFront(kioskPackage, enableKioskLock = true)
        extendKioskGrace(KIOSK_RECOVERY_GRACE_MS)
    }

    private fun bringPackageToFront(packageName: String, enableKioskLock: Boolean) {
        val opened = KioskManager.launchPackage(this, packageName, enableKioskLock)
        if (!opened) {
            Log.e("CommandService", "Nao foi possivel abrir app $packageName")
        }
    }

    private fun restartDevice() {
        val rebooted = KioskManager.rebootDevice(this)
        if (!rebooted) {
            Log.w("CommandService", "Reboot remoto indisponivel sem Device Owner ativo.")
        }
    }

    private fun extendKioskGrace(durationMs: Long) {
        kioskGraceUntilMs = System.currentTimeMillis() + durationMs
    }

    private fun restartSelf(triggerLaunch: Boolean) {
        if (!allowSelfRestart || !DeviceConfigStore.isRegistered(this)) {
            return
        }

        ContextCompat.startForegroundService(
            this,
            Intent(this, CommandService::class.java).apply {
                if (triggerLaunch) {
                    putExtra(EXTRA_TRIGGER_LAUNCH, true)
                }
            }
        )
    }

    private fun buildNotification(contentText: String): Notification {
        ensureNotificationChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Device Control",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantem o servico de controle remoto e kiosk ativo"
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        restartSelf(triggerLaunch = false)
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pollingJob?.cancel()
        kioskWatchdogJob?.cancel()
        serviceScope.cancel()
        restartSelf(triggerLaunch = false)
        super.onDestroy()
    }

    companion object {
        const val ACTION_RESTART_PACKAGE = "com.devicecontrolkiosk.action.RESTART_PACKAGE"
        const val ACTION_ENSURE_KIOSK = "com.devicecontrolkiosk.action.ENSURE_KIOSK"
        const val EXTRA_TRIGGER_LAUNCH = "extra_trigger_launch"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        private const val CHANNEL_ID = "device_control_service"
        private const val NOTIFICATION_ID = 1001
        private const val POLLING_INTERVAL_MS = 10_000L
        private const val KIOSK_WATCHDOG_INTERVAL_MS = 1_500L
        private const val APP_SWITCH_DELAY_MS = 1_200L
        private const val KIOSK_RESTORE_DELAY_MS = 3_000L
        private const val KIOSK_LAUNCH_GRACE_MS = 20_000L
        private const val KIOSK_RECOVERY_GRACE_MS = 4_000L
        private const val RESTART_DELAY_MS = 1_500L
    }
}
