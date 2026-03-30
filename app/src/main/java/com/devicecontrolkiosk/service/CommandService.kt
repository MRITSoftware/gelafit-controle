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
import com.devicecontrolkiosk.R
import com.devicecontrolkiosk.data.DeviceConfigStore
import com.devicecontrolkiosk.data.SupabaseApi
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Controle remoto ativo"))

        if (intent?.getBooleanExtra(EXTRA_TRIGGER_LAUNCH, false) == true) {
            serviceScope.launch {
                launchConfiguredApps()
            }
        }

        if (pollingJob == null) {
            pollingJob = serviceScope.launch {
                val config = DeviceConfigStore.getConfig(this@CommandService)
                val deviceId = config.deviceId
                if (deviceId.isNullOrBlank()) {
                    Log.e("CommandService", "Device ID nao encontrado. Servico nao pode iniciar polling.")
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
                    delay(10_000)
                }
            }
        }
        return START_STICKY
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

        val firstApp = config.controlledPackages.firstOrNull()
        val secondApp = config.controlledPackages.getOrNull(1)
        if (restartFirst) {
            config.controlledPackages.forEach { packageName ->
                restartApp(packageName, preserveKiosk = false)
                delay(RESTART_DELAY_MS)
            }
            return
        }

        firstApp?.let {
            openApp(it)
        }

        val kioskPackage = resolveKioskPackage(config)
        val delayedTarget = kioskPackage ?: secondApp
        val immediateSecond = secondApp?.takeIf { it != delayedTarget }

        if (immediateSecond != null) {
            delay(APP_LAUNCH_DELAY_MS)
            openApp(immediateSecond)
        }

        delayedTarget?.let {
            delay(KIOSK_LAUNCH_DELAY_MS)
            openApp(it)
        }
    }

    private suspend fun restartApp(packageName: String, preserveKiosk: Boolean) {
        Log.i("CommandService", "Reiniciando app: $packageName")
        try {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(packageName)
            delay(RESTART_DELAY_MS)
            openApp(packageName)
            val kioskPackage = resolveKioskPackage(DeviceConfigStore.getConfig(this))
            if (preserveKiosk && kioskPackage != null && kioskPackage != packageName) {
                delay(KIOSK_RESTORE_DELAY_MS)
                openApp(kioskPackage)
            }
        } catch (e: Exception) {
            Log.e("CommandService", "Erro ao reiniciar app: ${e.message}")
        }
    }

    private fun setKioskMode(packageName: String) {
        val config = DeviceConfigStore.getConfig(this)
        DeviceConfigStore.saveAppSelection(this, config.controlledPackages, packageName)
        serviceScope.launch {
            syncRemoteKioskSelection(config.controlledPackages, packageName)
        }
        Log.i("CommandService", "App de quiosque definido: $packageName")
        openApp(packageName)
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

    private fun openApp(packageName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            } else {
                Log.e("CommandService", "Nao foi possivel obter intent para $packageName")
            }
        } catch (e: Exception) {
            Log.e("CommandService", "Erro ao abrir app: ${e.message}")
        }
    }

    private fun restartDevice() {
        Log.i("CommandService", "Reiniciando dispositivo (placeholder)")
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
                description = "Mantem o servico de controle remoto ativo"
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pollingJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TRIGGER_LAUNCH = "extra_trigger_launch"
        private const val CHANNEL_ID = "device_control_service"
        private const val NOTIFICATION_ID = 1001
        private const val APP_LAUNCH_DELAY_MS = 2_000L
        private const val KIOSK_LAUNCH_DELAY_MS = 10_000L
        private const val KIOSK_RESTORE_DELAY_MS = 3_000L
        private const val RESTART_DELAY_MS = 1_500L
    }
}
